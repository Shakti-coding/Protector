package com.filevault.pro.presentation.screen.recovery

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filevault.pro.data.local.dao.FileEntryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import javax.inject.Inject

private const val TAG = "RecoveryViewModel"
private const val SHIZUKU_CODE = 1001

enum class ShizukuStatus { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }
enum class ScanMode { QUICK, DEEP }
enum class ScanState { IDLE, SCANNING, PAUSED, DONE }
enum class RecoveryFileType { ALL, PHOTO, VIDEO, AUDIO, DOCUMENT, ARCHIVE }

data class RecoveredFile(
    val id: String,
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val fileType: RecoveryFileType,
    val recoveryProbability: Int,
    val lastModified: Long,
    val source: String
)

data class RecoveryUiState(
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val scanMode: ScanMode = ScanMode.QUICK,
    val scanState: ScanState = ScanState.IDLE,
    val progress: Float = 0f,
    val scanPhase: String = "",
    val currentPath: String = "",
    val scannedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val blocksScanned: Int = 0,
    val totalBlocks: Int = 0,
    val foundFiles: List<RecoveredFile> = emptyList(),
    val filterType: RecoveryFileType = RecoveryFileType.ALL,
    val searchQuery: String = "",
    val selectedVolume: String = "Internal Storage",
    val availableVolumes: List<String> = listOf("Internal Storage"),
    val errorMessage: String? = null,
    val isGridView: Boolean = true,
    val gridColumns: Int = 3
)

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    application: Application,
    private val fileEntryDao: FileEntryDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    private val binderListener = Shizuku.OnBinderReceivedListener { refreshShizukuStatus() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refreshShizukuStatus() }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == SHIZUKU_CODE) refreshShizukuStatus()
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshShizukuStatus()
        detectVolumes()
    }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    fun refreshShizukuStatus() {
        val status = try {
            val pm = getApplication<Application>().packageManager
            runCatching { pm.getPackageInfo("moe.shizuku.privileged.api", 0) }
                .fold(
                    onSuccess = {
                        when {
                            !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
                            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                                ShizukuStatus.NO_PERMISSION
                            else -> ShizukuStatus.READY
                        }
                    },
                    onFailure = { ShizukuStatus.NOT_INSTALLED }
                )
        } catch (e: Exception) {
            ShizukuStatus.NOT_INSTALLED
        }
        _uiState.update { it.copy(shizukuStatus = status) }
    }

    fun requestShizukuPermission() {
        runCatching {
            if (Shizuku.isPreV11()) return
            Shizuku.requestPermission(SHIZUKU_CODE)
        }.onFailure { Log.e(TAG, "requestPermission failed", it) }
    }

    private fun detectVolumes() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val volumes = mutableListOf("Internal Storage")
            ctx.getExternalFilesDirs(null).forEachIndexed { i, f ->
                if (i > 0 && f != null) volumes.add("SD Card $i")
            }
            _uiState.update { it.copy(availableVolumes = volumes) }
        }
    }

    fun setScanMode(mode: ScanMode) = _uiState.update { it.copy(scanMode = mode) }
    fun setFilterType(type: RecoveryFileType) = _uiState.update { it.copy(filterType = type) }
    fun setSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun setVolume(vol: String) = _uiState.update { it.copy(selectedVolume = vol) }
    fun toggleView() = _uiState.update { it.copy(isGridView = !it.isGridView) }
    fun setGridColumns(cols: Int) = _uiState.update { it.copy(gridColumns = cols) }

    fun startScan() {
        if (_uiState.value.scanState == ScanState.SCANNING) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    scanState = ScanState.SCANNING,
                    progress = 0f,
                    scanPhase = "Initializing…",
                    currentPath = "",
                    scannedBytes = 0L,
                    totalBytes = 0L,
                    blocksScanned = 0,
                    totalBlocks = 0,
                    errorMessage = null
                )
            }
            try {
                if (_uiState.value.scanMode == ScanMode.QUICK) runQuickScan()
                else runDeepScan()
            } catch (e: Exception) {
                Log.e(TAG, "Scan error", e)
                _uiState.update { it.copy(scanState = ScanState.DONE, errorMessage = "Scan failed: ${e.message}") }
            }
        }
    }

    fun pauseScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(scanState = ScanState.PAUSED, scanPhase = "Paused") }
    }

    fun stopScan() {
        scanJob?.cancel()
        _uiState.update {
            it.copy(scanState = ScanState.IDLE, progress = 0f, scanPhase = "", currentPath = "")
        }
    }

    private suspend fun runQuickScan() = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val catalogedPaths = fileEntryDao.getAllNonDeletedPaths().toHashSet()
        val found = mutableListOf<RecoveredFile>()

        phase("Scanning MediaStore trash…", 0.05f)
        found.addAll(scanMediaTrash(ctx, catalogedPaths))
        progress(0.15f, found)

        phase("Scanning LOST.DIR…", 0.15f)
        found.addAll(scanLostDir(catalogedPaths) { path ->
            _uiState.update { it.copy(currentPath = path) }
        })
        progress(0.25f, found)

        phase("Scanning all storage files…", 0.25f)
        found.addAll(scanAllAccessibleFiles(catalogedPaths) { path, prog ->
            _uiState.update { it.copy(currentPath = path, progress = 0.25f + prog * 0.75f) }
        })

        _uiState.update {
            it.copy(
                progress = 1f,
                scanPhase = "Complete",
                currentPath = "",
                foundFiles = found.toList(),
                scanState = ScanState.DONE
            )
        }
    }

    private suspend fun runDeepScan() = withContext(Dispatchers.IO) {
        if (_uiState.value.shizukuStatus != ShizukuStatus.READY) {
            _uiState.update {
                it.copy(
                    scanState = ScanState.DONE,
                    errorMessage = "Deep scan requires Shizuku.\nInstall Shizuku, start it via ADB or root, then grant permission."
                )
            }
            return@withContext
        }
        val ctx = getApplication<Application>()
        val catalogedPaths = fileEntryDao.getAllNonDeletedPaths().toHashSet()
        val found = mutableListOf<RecoveredFile>()

        phase("Scanning MediaStore trash…", 0.02f)
        found.addAll(scanMediaTrash(ctx, catalogedPaths))
        progress(0.1f, found)

        phase("Scanning LOST.DIR…", 0.1f)
        found.addAll(scanLostDir(catalogedPaths) { path ->
            _uiState.update { it.copy(currentPath = path) }
        })
        progress(0.2f, found)

        phase("Scanning hidden files…", 0.2f)
        found.addAll(scanHiddenFiles(catalogedPaths) { path ->
            _uiState.update { it.copy(currentPath = path) }
        })
        progress(0.3f, found)

        phase("Detecting block devices via Shizuku…", 0.3f)
        val blockDevices = detectBlockDevicesViaShizuku()
        progress(0.35f, found)

        if (blockDevices.isEmpty()) {
            phase("No readable block devices found — using file-level scan", 0.35f)
            delay(800)
            found.addAll(scanExtendedFilesystem(catalogedPaths) { path, prog ->
                _uiState.update { it.copy(currentPath = path, progress = 0.35f + prog * 0.65f) }
            })
        } else {
            val totalSize = blockDevices.sumOf { it.second }
            var processedBytes = 0L
            val totalBlocks = blockDevices.sumOf { (_, size) -> ((size / CHUNK_SIZE) + 1).toInt() }
            _uiState.update { it.copy(totalBytes = totalSize, totalBlocks = totalBlocks) }

            blockDevices.forEachIndexed { devIdx, (devPath, devSize) ->
                if (!isActive) return@forEachIndexed
                phase("Deep scanning: $devPath", 0.35f + (processedBytes.toFloat() / totalSize.coerceAtLeast(1)) * 0.65f)
                found.addAll(
                    scanBlockDeviceViaShizuku(devPath, devSize, catalogedPaths) { bytesRead, blocksRead ->
                        processedBytes += bytesRead
                        val overallProg = 0.35f + (processedBytes.toFloat() / totalSize.coerceAtLeast(1)) * 0.65f
                        _uiState.update {
                            it.copy(
                                progress = overallProg.coerceIn(0f, 1f),
                                scannedBytes = processedBytes,
                                blocksScanned = it.blocksScanned + blocksRead,
                                currentPath = devPath,
                                foundFiles = found.toList()
                            )
                        }
                    }
                )
            }
        }

        _uiState.update {
            it.copy(
                progress = 1f,
                scanPhase = "Deep scan complete",
                currentPath = "",
                foundFiles = found.toList(),
                scanState = ScanState.DONE
            )
        }
    }

    private fun phase(label: String, prog: Float) {
        _uiState.update { it.copy(scanPhase = label, progress = prog) }
    }

    private fun progress(prog: Float, found: List<RecoveredFile>) {
        _uiState.update { it.copy(progress = prog, foundFiles = found.toList()) }
    }

    private fun shizukuExec(vararg args: String): Process? = runCatching {
        val cls = Class.forName("rikka.shizuku.Shizuku")
        val method = cls.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        method.invoke(null, args, null, null) as Process
    }.onFailure { Log.w(TAG, "shizukuExec(${args.toList()}) failed: ${it.message}") }.getOrNull()

    private fun detectBlockDevicesViaShizuku(): List<Pair<String, Long>> {
        val devices = mutableListOf<Pair<String, Long>>()
        val proc = shizukuExec("sh", "-c", "ls -l /dev/block/") ?: return devices
        runCatching {
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out.lines().forEach { line ->
                if (line.startsWith("b") || line.contains("mmcblk") || line.contains("sda")) {
                    val parts = line.trim().split(Regex("\\s+"))
                    val name = parts.lastOrNull() ?: return@forEach
                    val devPath = if (name.startsWith("/")) name else "/dev/block/$name"
                    val sizeBytes = getBlockDeviceSizeViaShizuku(devPath)
                    if (sizeBytes > 0) devices.add(devPath to sizeBytes)
                }
            }
        }.onFailure { Log.w(TAG, "detectBlockDevices failed: ${it.message}") }
        return devices
    }

    private fun getBlockDeviceSizeViaShizuku(devPath: String): Long {
        val proc = shizukuExec("sh", "-c", "blockdev --getsize64 $devPath 2>/dev/null || echo 0")
            ?: return 0L
        return runCatching {
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            out.toLongOrNull() ?: 0L
        }.getOrElse { 0L }
    }

    private suspend fun scanBlockDeviceViaShizuku(
        devPath: String,
        devSize: Long,
        catalogedPaths: Set<String>,
        onProgress: suspend (bytesRead: Long, blocksRead: Int) -> Unit
    ): List<RecoveredFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<RecoveredFile>()
        val totalChunks = ((devSize / CHUNK_SIZE) + 1).toInt().coerceAtMost(MAX_CHUNKS)

        runCatching {
            repeat(totalChunks) { chunkIdx ->
                if (!isActive) return@repeat
                val skip = chunkIdx.toLong()
                val cmd = "dd if=$devPath bs=$CHUNK_SIZE count=1 skip=$skip 2>/dev/null"
                val proc = shizukuExec("sh", "-c", cmd) ?: return@repeat
                val data = proc.inputStream.readBytes()
                proc.waitFor()

                val carved = carveSignatures(data, devPath, chunkIdx, catalogedPaths)
                result.addAll(carved)
                onProgress(CHUNK_SIZE, 1)
                delay(10)
            }
        }.onFailure { Log.w(TAG, "Block scan error on $devPath: ${it.message}") }
        result
    }

    private fun carveSignatures(
        data: ByteArray,
        devPath: String,
        chunkIdx: Int,
        catalogedPaths: Set<String>
    ): List<RecoveredFile> {
        val result = mutableListOf<RecoveredFile>()
        FILE_SIGNATURES.forEach { (sig, mime, ext) ->
            var offset = 0
            while (offset <= data.size - sig.size) {
                val found = sig.indices.all { data[offset + it] == sig[it] }
                if (found) {
                    val id = "carved_${devPath.hashCode()}_${chunkIdx}_$offset"
                    val name = "recovered_${chunkIdx}_$offset.$ext"
                    val estimatedSize = estimateCarvedSize(data, offset, sig.size)
                    result.add(
                        RecoveredFile(
                            id = id,
                            path = "/dev/block/carved/$name",
                            name = name,
                            sizeBytes = estimatedSize,
                            mimeType = mime,
                            fileType = mimeToRecoveryType(mime),
                            recoveryProbability = 60,
                            lastModified = System.currentTimeMillis(),
                            source = "Deep Scan: $devPath"
                        )
                    )
                }
                offset++
            }
        }
        return result
    }

    private fun estimateCarvedSize(data: ByteArray, start: Int, sigSize: Int): Long {
        return ((data.size - start) * 2L).coerceAtLeast(1024L)
    }

    private suspend fun scanExtendedFilesystem(
        catalogedPaths: Set<String>,
        onProgress: suspend (path: String, progress: Float) -> Unit
    ): List<RecoveredFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<RecoveredFile>()
        val proc = shizukuExec("sh", "-c", "find /sdcard /storage -type f 2>/dev/null")
        runCatching {
            val lines = proc?.inputStream?.bufferedReader()?.readLines() ?: emptyList()
            proc?.waitFor()
            lines.forEachIndexed { i, path ->
                if (!isActive) return@forEachIndexed
                if (path !in catalogedPaths) {
                    val f = File(path)
                    if (f.exists() && f.isFile) {
                        val mime = guessMime(f.name)
                        result.add(
                            RecoveredFile(
                                id = "ext_${path.hashCode()}",
                                path = path,
                                name = f.name,
                                sizeBytes = f.length(),
                                mimeType = mime,
                                fileType = guessType(f.name),
                                recoveryProbability = 75,
                                lastModified = f.lastModified(),
                                source = "Extended Scan"
                            )
                        )
                    }
                }
                if (i % 50 == 0) onProgress(path, i.toFloat() / lines.size.coerceAtLeast(1))
            }
        }.onFailure { Log.w(TAG, "extFS scan failed: ${it.message}") }
        result
    }

    private fun scanMediaTrash(ctx: Context, catalogedPaths: Set<String>): List<RecoveredFile> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val result = mutableListOf<RecoveredFile>()
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        runCatching {
            ctx.contentResolver.query(
                uri, projection,
                "${MediaStore.Files.FileColumns.IS_TRASHED} = 1",
                null, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val path = cursor.getString(dataCol)
                        ?: ContentUris.withAppendedId(uri, id).toString()
                    if (path in catalogedPaths) continue
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "*/*"
                    val modified = cursor.getLong(dateCol) * 1000L
                    result.add(
                        RecoveredFile(
                            id = "trash_$id",
                            path = path,
                            name = name,
                            sizeBytes = size,
                            mimeType = mime,
                            fileType = mimeToRecoveryType(mime),
                            recoveryProbability = 95,
                            lastModified = modified,
                            source = "MediaStore Trash"
                        )
                    )
                }
            }
        }
        return result
    }

    private fun scanLostDir(
        catalogedPaths: Set<String>,
        onPath: (String) -> Unit = {}
    ): List<RecoveredFile> {
        val result = mutableListOf<RecoveredFile>()
        val storageRoot = Environment.getExternalStorageDirectory()
        listOf(File(storageRoot, "LOST.DIR"), File(storageRoot, "lost+found")).forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    onPath(f.absolutePath)
                    if (f.absolutePath !in catalogedPaths) {
                        result.add(
                            RecoveredFile(
                                id = "lost_${f.absolutePath.hashCode()}",
                                path = f.absolutePath,
                                name = f.name,
                                sizeBytes = f.length(),
                                mimeType = guessMime(f.name),
                                fileType = guessType(f.name),
                                recoveryProbability = 70,
                                lastModified = f.lastModified(),
                                source = "LOST.DIR"
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    private fun scanHiddenFiles(
        catalogedPaths: Set<String>,
        onPath: (String) -> Unit = {}
    ): List<RecoveredFile> {
        val result = mutableListOf<RecoveredFile>()
        val storageRoot = Environment.getExternalStorageDirectory()
        runCatching {
            storageRoot.walkTopDown()
                .filter { it.isFile && it.name.startsWith(".") && it.length() > 0 }
                .take(500)
                .forEach { f ->
                    onPath(f.absolutePath)
                    if (f.absolutePath !in catalogedPaths) {
                        result.add(
                            RecoveredFile(
                                id = "hidden_${f.absolutePath.hashCode()}",
                                path = f.absolutePath,
                                name = f.name.removePrefix("."),
                                sizeBytes = f.length(),
                                mimeType = guessMime(f.name),
                                fileType = guessType(f.name),
                                recoveryProbability = 50,
                                lastModified = f.lastModified(),
                                source = "Hidden Files"
                            )
                        )
                    }
                }
        }
        return result
    }

    private suspend fun scanAllAccessibleFiles(
        catalogedPaths: Set<String>,
        onProgress: suspend (path: String, progress: Float) -> Unit = { _, _ -> }
    ): List<RecoveredFile> = withContext(Dispatchers.IO) {
        val knownExtensions = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tiff", "raw", "cr2", "nef",
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm", "ts", "m4v",
            "mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "epub",
            "zip", "rar", "7z", "tar", "gz", "bz2",
            "apk", "json", "xml", "html", "htm", "log", "md"
        )
        val result = mutableListOf<RecoveredFile>()
        val storageRoot = Environment.getExternalStorageDirectory()
        val alreadyAdded = mutableSetOf<String>()
        runCatching {
            val allFiles = storageRoot.walkTopDown()
                .filter { it.isFile && it.length() > 0 &&
                    it.extension.lowercase() in knownExtensions }
                .toList()
            val total = allFiles.size.coerceAtLeast(1)
            allFiles.forEachIndexed { i, f ->
                if (!isActive) return@forEachIndexed
                val absPath = f.absolutePath
                if (absPath !in catalogedPaths && absPath !in alreadyAdded) {
                    alreadyAdded.add(absPath)
                    val isHidden = f.name.startsWith(".")
                    val probability = when {
                        isHidden -> 50
                        absPath.contains("LOST.DIR") || absPath.contains("lost+found") -> 70
                        else -> 85
                    }
                    val source = when {
                        isHidden -> "Hidden Files"
                        absPath.contains("LOST.DIR") -> "LOST.DIR"
                        else -> "Storage Scan"
                    }
                    result.add(
                        RecoveredFile(
                            id = "scan_${absPath.hashCode()}",
                            path = absPath,
                            name = if (isHidden) f.name.removePrefix(".") else f.name,
                            sizeBytes = f.length(),
                            mimeType = guessMime(f.name),
                            fileType = guessType(f.name),
                            recoveryProbability = probability,
                            lastModified = f.lastModified(),
                            source = source
                        )
                    )
                }
                if (i % 100 == 0) {
                    onProgress(absPath, i.toFloat() / total)
                }
            }
        }.onFailure { Log.w(TAG, "scanAllAccessibleFiles failed: ${it.message}") }
        result
    }

    fun filteredFiles(): List<RecoveredFile> {
        val state = _uiState.value
        return state.foundFiles.filter { f ->
            (state.filterType == RecoveryFileType.ALL || f.fileType == state.filterType) &&
            (state.searchQuery.isBlank() || f.name.contains(state.searchQuery, ignoreCase = true))
        }
    }

    private fun mimeToRecoveryType(mime: String): RecoveryFileType = when {
        mime.startsWith("image/") -> RecoveryFileType.PHOTO
        mime.startsWith("video/") -> RecoveryFileType.VIDEO
        mime.startsWith("audio/") -> RecoveryFileType.AUDIO
        mime.contains("pdf") || mime.contains("word") || mime.contains("text") ||
        mime.contains("spreadsheet") -> RecoveryFileType.DOCUMENT
        mime.contains("zip") || mime.contains("rar") || mime.contains("7z") ||
        mime.contains("tar") -> RecoveryFileType.ARCHIVE
        else -> RecoveryFileType.DOCUMENT
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/avi"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            else -> "application/octet-stream"
        }
    }

    private fun guessType(name: String): RecoveryFileType {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tiff", "raw", "cr2", "nef" -> RecoveryFileType.PHOTO
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm", "ts", "m4v" -> RecoveryFileType.VIDEO
            "mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma" -> RecoveryFileType.AUDIO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "epub" -> RecoveryFileType.DOCUMENT
            "zip", "rar", "7z", "tar", "gz", "bz2" -> RecoveryFileType.ARCHIVE
            else -> RecoveryFileType.DOCUMENT
        }
    }

    companion object {
        private const val CHUNK_SIZE = 1024L * 1024L * 4L  // 4 MB per chunk
        private const val MAX_CHUNKS = 500                  // max 2 GB scanned per device

        private val FILE_SIGNATURES: List<Triple<ByteArray, String, String>> = listOf(
            Triple(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), "image/jpeg", "jpg"),
            Triple(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), "image/png", "png"),
            Triple(byteArrayOf(0x47, 0x49, 0x46, 0x38), "image/gif", "gif"),
            Triple(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70), "video/mp4", "mp4"),
            Triple(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()), "video/x-matroska", "mkv"),
            Triple(byteArrayOf(0x49, 0x44, 0x33), "audio/mpeg", "mp3"),
            Triple(byteArrayOf(0x66, 0x4C, 0x61, 0x43), "audio/flac", "flac"),
            Triple(byteArrayOf(0x25, 0x50, 0x44, 0x46), "application/pdf", "pdf"),
            Triple(byteArrayOf(0x50, 0x4B, 0x03, 0x04), "application/zip", "zip"),
        )
    }
}
