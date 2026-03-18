package com.filevault.pro.presentation.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filevault.pro.data.preferences.AppPreferences
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileFilter
import com.filevault.pro.domain.model.FileType
import com.filevault.pro.domain.model.SortOrder
import com.filevault.pro.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val fileRepository: FileRepository
) : ViewModel() {

    val themeMode: StateFlow<String> = appPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val showHiddenFiles: StateFlow<Boolean> = appPreferences.showHiddenFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLockEnabled: StateFlow<Boolean> = appPreferences.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scanIntervalMinutes: StateFlow<Int> = appPreferences.scanIntervalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatus() { _statusMessage.value = null }

    fun setShowHiddenFiles(show: Boolean) = viewModelScope.launch {
        appPreferences.setShowHiddenFiles(show)
    }

    fun setAppLockEnabled(enabled: Boolean) = viewModelScope.launch {
        appPreferences.setAppLockEnabled(enabled)
    }

    fun setScanIntervalMinutes(minutes: Int) = viewModelScope.launch {
        appPreferences.setScanIntervalMinutes(minutes)
    }

    fun cycleTheme() = viewModelScope.launch {
        val current = appPreferences.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM").value
        val next = when (current) {
            "SYSTEM" -> "LIGHT"
            "LIGHT" -> "DARK"
            else -> "SYSTEM"
        }
        appPreferences.setThemeMode(next)
    }

    private suspend fun getFiles(): List<FileEntry> =
        fileRepository.getAllFiles(SortOrder(), FileFilter()).first()

    fun exportCatalogCsv(context: Context) {
        viewModelScope.launch {
            try {
                val files = getFiles()
                val sb = StringBuilder()
                sb.appendLine("path,name,folder,size_bytes,last_modified,mime_type,file_type,width,height,duration_ms,date_added")
                files.forEach { f ->
                    sb.appendLine("\"${f.path}\",\"${f.name}\",\"${f.folderName}\",${f.sizeBytes},${f.lastModified},\"${f.mimeType}\",${f.fileType.name},${f.width ?: ""},${f.height ?: ""},${f.durationMs ?: ""},${f.dateAdded}")
                }
                val exportFile = File(context.getExternalFilesDir(null), "filevault_catalog_${System.currentTimeMillis()}.csv")
                exportFile.writeText(sb.toString())
                shareFile(context, exportFile, "text/csv")
                _statusMessage.value = "Exported ${files.size} entries as CSV"
            } catch (e: Exception) {
                _statusMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun exportCatalogJson(context: Context) {
        viewModelScope.launch {
            try {
                val files = getFiles()
                val array = JSONArray()
                files.forEach { f ->
                    val obj = JSONObject()
                    obj.put("path", f.path)
                    obj.put("name", f.name)
                    obj.put("folder_path", f.folderPath)
                    obj.put("folder_name", f.folderName)
                    obj.put("size_bytes", f.sizeBytes)
                    obj.put("last_modified", f.lastModified)
                    obj.put("mime_type", f.mimeType)
                    obj.put("file_type", f.fileType.name)
                    obj.put("date_added", f.dateAdded)
                    f.width?.let { obj.put("width", it) }
                    f.height?.let { obj.put("height", it) }
                    f.durationMs?.let { obj.put("duration_ms", it) }
                    f.orientation?.let { obj.put("orientation", it) }
                    f.cameraMake?.let { obj.put("camera_make", it) }
                    f.cameraModel?.let { obj.put("camera_model", it) }
                    obj.put("has_gps", f.hasGps)
                    f.dateTaken?.let { obj.put("date_taken", it) }
                    obj.put("is_hidden", f.isHidden)
                    f.contentHash?.let { obj.put("content_hash", it) }
                    obj.put("is_sync_ignored", f.isSyncIgnored)
                    f.lastSyncedAt?.let { obj.put("last_synced_at", it) }
                    obj.put("is_deleted_from_device", f.isDeletedFromDevice)
                    array.put(obj)
                }
                val exportFile = File(context.getExternalFilesDir(null), "filevault_catalog_${System.currentTimeMillis()}.json")
                exportFile.writeText(array.toString(2))
                shareFile(context, exportFile, "application/json")
                _statusMessage.value = "Exported ${files.size} entries as JSON"
            } catch (e: Exception) {
                _statusMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun importCatalog(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: throw IllegalStateException("Could not read file")
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val uriPath = uri.lastPathSegment ?: ""
                val isJson = mimeType.contains("json") || uriPath.endsWith(".json", ignoreCase = true)
                    || content.trimStart().startsWith("[")
                val entries = if (isJson) parseJson(content) else parseCsv(content)
                fileRepository.upsertFiles(entries)
                _statusMessage.value = "Imported ${entries.size} entries"
            } catch (e: Exception) {
                _statusMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    private fun parseJson(content: String): List<FileEntry> {
        val array = JSONArray(content)
        val result = mutableListOf<FileEntry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val path = obj.optString("path").takeIf { it.isNotBlank() } ?: continue
            val folderPath = obj.optString("folder_path").ifBlank { path.substringBeforeLast("/") }
            result.add(
                FileEntry(
                    path = path,
                    name = obj.optString("name").ifBlank { path.substringAfterLast("/") },
                    folderPath = folderPath,
                    folderName = obj.optString("folder_name").ifBlank { folderPath.substringAfterLast("/") },
                    sizeBytes = obj.optLong("size_bytes", 0L),
                    lastModified = obj.optLong("last_modified", 0L),
                    mimeType = obj.optString("mime_type", "application/octet-stream"),
                    fileType = runCatching { FileType.valueOf(obj.optString("file_type", "OTHER")) }.getOrDefault(FileType.OTHER),
                    dateAdded = obj.optLong("date_added", System.currentTimeMillis()),
                    width = if (obj.has("width")) obj.getInt("width") else null,
                    height = if (obj.has("height")) obj.getInt("height") else null,
                    durationMs = if (obj.has("duration_ms")) obj.getLong("duration_ms") else null,
                    orientation = if (obj.has("orientation")) obj.getInt("orientation") else null,
                    cameraMake = obj.optString("camera_make").takeIf { it.isNotBlank() },
                    cameraModel = obj.optString("camera_model").takeIf { it.isNotBlank() },
                    hasGps = obj.optBoolean("has_gps", false),
                    dateTaken = if (obj.has("date_taken")) obj.getLong("date_taken") else null,
                    isHidden = obj.optBoolean("is_hidden", false),
                    contentHash = obj.optString("content_hash").takeIf { it.isNotBlank() },
                    isSyncIgnored = obj.optBoolean("is_sync_ignored", false),
                    lastSyncedAt = if (obj.has("last_synced_at")) obj.getLong("last_synced_at") else null,
                    isDeletedFromDevice = obj.optBoolean("is_deleted_from_device", false)
                )
            )
        }
        return result
    }

    private fun parseCsv(content: String): List<FileEntry> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val result = mutableListOf<FileEntry>()
        for (line in lines.drop(1)) {
            val cols = splitCsvLine(line)
            if (cols.size < 7) continue
            val path = cols.getOrElse(0) { "" }.unquote().takeIf { it.isNotBlank() } ?: continue
            val folderPath = path.substringBeforeLast("/")
            result.add(
                FileEntry(
                    path = path,
                    name = cols.getOrElse(1) { "" }.unquote().ifBlank { path.substringAfterLast("/") },
                    folderPath = folderPath,
                    folderName = cols.getOrElse(2) { "" }.unquote().ifBlank { folderPath.substringAfterLast("/") },
                    sizeBytes = cols.getOrElse(3) { "0" }.toLongOrNull() ?: 0L,
                    lastModified = cols.getOrElse(4) { "0" }.toLongOrNull() ?: 0L,
                    mimeType = cols.getOrElse(5) { "" }.unquote().ifBlank { "application/octet-stream" },
                    fileType = runCatching { FileType.valueOf(cols.getOrElse(6) { "OTHER" }.unquote()) }.getOrDefault(FileType.OTHER),
                    width = cols.getOrElse(7) { "" }.toIntOrNull(),
                    height = cols.getOrElse(8) { "" }.toIntOrNull(),
                    durationMs = cols.getOrElse(9) { "" }.toLongOrNull(),
                    dateAdded = cols.getOrElse(10) { "0" }.toLongOrNull() ?: System.currentTimeMillis()
                )
            )
        }
        return result
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { result.add(sb.toString()); sb.clear() }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }

    private fun String.unquote() = trim().removeSurrounding("\"")

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Catalog"))
    }

    fun exportCatalog(context: Context) = exportCatalogCsv(context)
}
