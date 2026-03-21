package com.filevault.pro.presentation.screen.fileviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.filevault.pro.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipFile as JavaZipFile

enum class ViewerMode { TEXT, JSON, BINARY_INFO, ARCHIVE_INFO, PDF }

data class ZipEntryInfo(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean
)

data class ViewerState(
    val mode: ViewerMode = ViewerMode.TEXT,
    val lines: List<String> = emptyList(),
    val fileInfo: Map<String, String> = emptyMap(),
    val zipEntries: List<ZipEntryInfo> = emptyList(),
    val pdfPageCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

@Composable
fun UniversalFileViewerScreen(
    path: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val decodedPath = remember(path) { try { URLDecoder.decode(path, "UTF-8") } catch (_: Exception) { path } }
    val file = remember(decodedPath) { File(decodedPath) }

    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ViewerState()) }
    var fontSize by remember { mutableIntStateOf(14) }
    var wordWrap by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showUnzipProgress by remember { mutableStateOf(false) }
    var unzipMessage by remember { mutableStateOf("") }
    var showFileInfo by remember { mutableStateOf(false) }

    val unzipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                showUnzipProgress = true
                unzipMessage = "Extracting…"
                try {
                    val destFolder = DocumentFile.fromTreeUri(context, uri) ?: return@launch
                    JavaZipFile(file).use { zip ->
                        var extracted = 0
                        for (entry in zip.entries()) {
                            if (entry.isDirectory) continue
                            val entryName = entry.name
                            val parts = entryName.split("/")
                            var parent = destFolder
                            for (i in 0 until parts.size - 1) {
                                parent = parent.findFile(parts[i])
                                    ?: parent.createDirectory(parts[i])
                                    ?: break
                            }
                            val lastName = parts.last()
                            val mime = FileUtils.getMimeType(File(lastName)).ifBlank { "*/*" }
                            val destFile = parent.createFile(mime, lastName) ?: continue
                            context.contentResolver.openOutputStream(destFile.uri)?.use { out ->
                                zip.getInputStream(entry).use { it.copyTo(out) }
                            }
                            extracted++
                            unzipMessage = "Extracted $extracted files…"
                        }
                        unzipMessage = "Done! $extracted files extracted."
                    }
                } catch (e: Exception) {
                    unzipMessage = "Error: ${e.message}"
                } finally {
                    kotlinx.coroutines.delay(2000)
                    showUnzipProgress = false
                }
            }
        }
    }

    LaunchedEffect(decodedPath) {
        state = ViewerState(isLoading = true)
        state = withContext(Dispatchers.IO) {
            loadFile(file)
        }
    }

    val filteredLines = remember(state.lines, searchQuery) {
        if (searchQuery.isBlank()) state.lines
        else state.lines.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            file.name,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        if (state.mode == ViewerMode.TEXT || state.mode == ViewerMode.JSON) {
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(Icons.Default.Search, "Search")
                            }
                            IconButton(onClick = { if (fontSize < 24) fontSize++ }) {
                                Icon(Icons.Default.ZoomIn, "Larger text")
                            }
                            IconButton(onClick = { if (fontSize > 8) fontSize-- }) {
                                Icon(Icons.Default.ZoomOut, "Smaller text")
                            }
                            IconButton(onClick = {
                                val text = filteredLines.joinToString("\n")
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText(file.name, text))
                            }) {
                                Icon(Icons.Default.ContentCopy, "Copy content")
                            }
                        }
                        IconButton(onClick = { showFileInfo = !showFileInfo }) {
                            Icon(Icons.Default.Info, "File Info")
                        }
                        IconButton(onClick = {
                            val fileUri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "*/*"
                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share File"))
                        }) {
                            Icon(Icons.Default.Share, "Share")
                        }
                        IconButton(onClick = {
                            val fileUri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fileUri, getMimeForExt(file.extension))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }
                        }) {
                            Icon(Icons.Default.OpenInNew, "Open With")
                        }
                    }
                )
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search in file…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        singleLine = true
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Text(state.error!!, color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            val fileUri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fileUri, "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }
                        }) {
                            Icon(Icons.Default.OpenInNew, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open With External App")
                        }
                    }
                }
                state.mode == ViewerMode.PDF -> {
                    PdfView(file = file, pageCount = state.pdfPageCount)
                }
                state.mode == ViewerMode.ARCHIVE_INFO -> {
                    ZipView(
                        file = file,
                        entries = state.zipEntries,
                        extraInfo = state.fileInfo,
                        onUnzip = { unzipLauncher.launch(null) }
                    )
                }
                state.mode == ViewerMode.BINARY_INFO -> {
                    FileInfoView(file = file, extraInfo = state.fileInfo)
                }
                state.mode == ViewerMode.JSON -> {
                    JsonView(
                        lines = filteredLines,
                        fontSize = fontSize,
                        wordWrap = wordWrap,
                        searchQuery = searchQuery
                    )
                }
                else -> {
                    TextView(
                        lines = filteredLines,
                        fontSize = fontSize,
                        wordWrap = wordWrap,
                        searchQuery = searchQuery
                    )
                }
            }

            if (!state.isLoading && state.error == null && (state.mode == ViewerMode.TEXT || state.mode == ViewerMode.JSON)) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${if (searchQuery.isNotBlank()) "${filteredLines.size}/${state.lines.size}" else "${state.lines.size}"} lines  •  ${FileUtils.formatSize(file.length())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            if (wordWrap) Icons.Default.WrapText else Icons.Default.Code,
                            "Wrap",
                            Modifier
                                .size(18.dp)
                                .padding(start = 4.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (showUnzipProgress) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(unzipMessage, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showFileInfo) {
        AlertDialog(
            onDismissRequest = { showFileInfo = false },
            title = { Text("File Info", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val dateStr = remember(file) {
                        java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(file.lastModified()))
                    }
                    InfoRow("Name", file.name)
                    InfoRow("Size", FileUtils.formatSize(file.length()))
                    InfoRow("Path", file.parent ?: "")
                    InfoRow("Modified", dateStr)
                    InfoRow("Type", file.extension.uppercase().ifEmpty { "Unknown" })
                    if (state.mode == ViewerMode.PDF && state.pdfPageCount > 0)
                        InfoRow("Pages", state.pdfPageCount.toString())
                    if (state.mode == ViewerMode.TEXT || state.mode == ViewerMode.JSON)
                        InfoRow("Lines", state.lines.size.toString())
                    if (state.mode == ViewerMode.ARCHIVE_INFO)
                        InfoRow("Entries", state.zipEntries.size.toString())
                }
            },
            confirmButton = {
                TextButton(onClick = { showFileInfo = false }) { Text("Close") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PdfView(file: File, pageCount: Int) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    if (pageCount == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PictureAsPdf, null, Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Text("Cannot render PDF pages", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("Use 'Open With' button to view in external app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }
        return
    }

    val pageIndices = (0 until pageCount).toList()
    val bitmapCache = remember { mutableMapOf<Int, Bitmap?>() }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("$pageCount page${if (pageCount > 1) "s" else ""}  •  ${FileUtils.formatSize(file.length())}",
                    style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text("Page ${(listState.firstVisibleItemIndex + 1).coerceAtMost(pageCount)} of $pageCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(Color(0xFF404040)),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pageIndices, key = { it }) { pageIndex ->
                var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(bitmapCache[pageIndex]) }
                LaunchedEffect(pageIndex) {
                    if (bitmap == null) {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                                    PdfRenderer(fd).use { renderer ->
                                        renderer.openPage(pageIndex).use { page ->
                                            val widthPx = (density.density * 360).toInt().coerceAtLeast(480)
                                            val heightPx = (widthPx * page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)).toInt()
                                            val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                                            bmp.eraseColor(android.graphics.Color.WHITE)
                                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                            bmp
                                        }
                                    }
                                }
                            }.getOrNull()
                        }.also { rendered ->
                            if (rendered != null) {
                                bitmapCache[pageIndex] = rendered
                                bitmap = rendered
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1}",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.77f)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Page ${pageIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZipView(
    file: File,
    entries: List<ZipEntryInfo>,
    extraInfo: Map<String, String>,
    onUnzip: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderZip, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                extraInfo["Files"]?.let { append("$it files") }
                                extraInfo["Folders"]?.let { if (isNotEmpty()) append(", "); append("$it folders") }
                                extraInfo["Compressed Size"]?.let { if (isNotEmpty()) append("  •  "); append(it) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onUnzip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Unarchive, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Unzip — Select Destination Folder")
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Empty archive or unsupported format",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(entries, key = { it.name }) { entry ->
                    ListItem(
                        headlineContent = {
                            Text(
                                entry.name.substringAfterLast("/").ifEmpty { entry.name },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        supportingContent = if (!entry.isDirectory) {
                            {
                                Text(
                                    entry.name.substringBeforeLast("/", "").ifEmpty { "/" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                                )
                            }
                        } else null,
                        leadingContent = {
                            Icon(
                                if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface.copy(0.5f)
                            )
                        },
                        trailingContent = if (!entry.isDirectory && entry.size > 0) {
                            { Text(FileUtils.formatSize(entry.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) }
                        } else null
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 52.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TextView(
    lines: List<String>,
    fontSize: Int,
    wordWrap: Boolean,
    searchQuery: String
) {
    val scrollH = rememberScrollState()

    if (wordWrap) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                Row {
                    Text(
                        "${index + 1}",
                        fontSize = (fontSize - 2).sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(40.dp)
                    )
                    Text(
                        buildHighlightedText(line, searchQuery),
                        fontSize = fontSize.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (index < lines.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(0.2f),
                        modifier = Modifier.padding(start = 40.dp)
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollH),
            contentPadding = PaddingValues(12.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                Row {
                    Text(
                        "${index + 1}",
                        fontSize = (fontSize - 2).sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(40.dp)
                    )
                    Text(
                        buildHighlightedText(line, searchQuery),
                        fontSize = fontSize.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonView(
    lines: List<String>,
    fontSize: Int,
    wordWrap: Boolean,
    searchQuery: String
) {
    val jsonColors = mapOf(
        "key" to Color(0xFF82AAFF),
        "string" to Color(0xFFC3E88D),
        "number" to Color(0xFFF78C6C),
        "bool" to Color(0xFFFF9CAC),
        "null" to Color(0xFFFF9CAC),
        "brace" to Color(0xFFFFCB6B)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2E)),
        contentPadding = PaddingValues(12.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            Row {
                Text(
                    "${index + 1}",
                    fontSize = (fontSize - 2).sp,
                    color = Color.White.copy(0.3f),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(44.dp)
                )
                Text(
                    buildJsonAnnotatedString(line, jsonColors, searchQuery),
                    fontSize = fontSize.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = if (wordWrap) Modifier.weight(1f) else Modifier
                )
            }
        }
    }
}

@Composable
private fun FileInfoView(file: File, extraInfo: Map<String, String>) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

    val baseInfo = buildMap {
        put("Name", file.name)
        put("Extension", file.extension.uppercase().ifEmpty { "None" })
        put("Size", FileUtils.formatSize(file.length()) + " (${file.length()} bytes)")
        put("Location", file.parent ?: "Unknown")
        put("Last Modified", dateFormat.format(Date(file.lastModified())))
        put("Readable", if (file.canRead()) "Yes" else "No")
        put("Writable", if (file.canWrite()) "Yes" else "No")
    } + extraInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.CenterHorizontally)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    getFileIcon(file.extension),
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    file.extension.uppercase().take(4).ifEmpty { "FILE" },
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(file.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                baseInfo.entries.forEachIndexed { i, (k, v) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            k,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            modifier = Modifier.width(100.dp)
                        )
                        Text(v, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    }
                    if (i < baseInfo.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
                        )
                    }
                }
            }
        }
    }
}

private fun buildHighlightedText(line: String, query: String) = buildAnnotatedString {
    if (query.isBlank()) {
        append(line)
        return@buildAnnotatedString
    }
    var start = 0
    val lower = line.lowercase()
    val lowerQuery = query.lowercase()
    while (true) {
        val idx = lower.indexOf(lowerQuery, start)
        if (idx == -1) {
            append(line.substring(start))
            break
        }
        append(line.substring(start, idx))
        withStyle(SpanStyle(background = Color(0xFFFFEB3B), color = Color.Black)) {
            append(line.substring(idx, idx + query.length))
        }
        start = idx + query.length
    }
}

private fun buildJsonAnnotatedString(
    line: String,
    colors: Map<String, Color>,
    searchQuery: String
) = buildAnnotatedString {
    val keyColor = colors["key"] ?: Color.White
    val stringColor = colors["string"] ?: Color.White
    val numberColor = colors["number"] ?: Color.White
    val boolNullColor = colors["bool"] ?: Color.White
    val braceColor = colors["brace"] ?: Color.White
    val defaultColor = Color.White

    var i = 0
    val s = line
    while (i < s.length) {
        val c = s[i]
        when {
            c == '"' -> {
                val end = s.indexOf('"', i + 1)
                val token = if (end == -1) s.substring(i) else s.substring(i, end + 1)
                val isKey = s.indexOf(':', end + 1).let { colon ->
                    colon != -1 && s.substring(end + 1, colon).isBlank()
                }
                withStyle(SpanStyle(color = if (isKey) keyColor else stringColor)) {
                    append(token)
                }
                i = if (end == -1) s.length else end + 1
            }
            c.isDigit() || (c == '-' && i + 1 < s.length && s[i + 1].isDigit()) -> {
                var j = i + 1
                while (j < s.length && (s[j].isDigit() || s[j] == '.' || s[j] == 'e' || s[j] == 'E' || s[j] == '+' || s[j] == '-')) j++
                withStyle(SpanStyle(color = numberColor)) { append(s.substring(i, j)) }
                i = j
            }
            s.startsWith("true", i) || s.startsWith("false", i) || s.startsWith("null", i) -> {
                val keyword = when {
                    s.startsWith("true", i) -> "true"
                    s.startsWith("false", i) -> "false"
                    else -> "null"
                }
                withStyle(SpanStyle(color = boolNullColor)) { append(keyword) }
                i += keyword.length
            }
            c == '{' || c == '}' || c == '[' || c == ']' -> {
                withStyle(SpanStyle(color = braceColor)) { append(c) }
                i++
            }
            else -> {
                withStyle(SpanStyle(color = defaultColor)) { append(c) }
                i++
            }
        }
    }
}

private fun getFileIcon(ext: String) = when (ext.lowercase()) {
    "pdf" -> Icons.Default.PictureAsPdf
    "doc", "docx" -> Icons.Default.Description
    "xls", "xlsx", "csv" -> Icons.Default.TableChart
    "ppt", "pptx" -> Icons.Default.Slideshow
    "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
    "apk" -> Icons.Default.Android
    "json" -> Icons.Default.DataObject
    "xml" -> Icons.Default.Code
    "html", "htm" -> Icons.Default.Html
    "md" -> Icons.Default.Article
    "txt" -> Icons.Default.Notes
    else -> Icons.Default.InsertDriveFile
}

private fun getMimeForExt(ext: String) = when (ext.lowercase()) {
    "pdf" -> "application/pdf"
    "json" -> "application/json"
    "xml" -> "application/xml"
    "html", "htm" -> "text/html"
    "csv" -> "text/csv"
    "txt", "md", "log" -> "text/plain"
    "zip" -> "application/zip"
    "apk" -> "application/vnd.android.package-archive"
    else -> "*/*"
}

private suspend fun loadFile(file: File): ViewerState = withContext(Dispatchers.IO) {
    if (!file.exists()) return@withContext ViewerState(isLoading = false, error = "File not found: ${file.path}")

    val ext = file.extension.lowercase()
    val sizeBytes = file.length()

    when {
        ext == "zip" -> {
            val entries = runCatching {
                JavaZipFile(file).use { zip ->
                    zip.entries().toList().map { entry ->
                        ZipEntryInfo(
                            name = entry.name,
                            size = entry.size.coerceAtLeast(0L),
                            compressedSize = entry.compressedSize.coerceAtLeast(0L),
                            isDirectory = entry.isDirectory
                        )
                    }.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                }
            }.getOrElse { emptyList() }
            val fileCount = entries.count { !it.isDirectory }
            val dirCount = entries.count { it.isDirectory }
            ViewerState(
                mode = ViewerMode.ARCHIVE_INFO,
                zipEntries = entries,
                fileInfo = buildMap {
                    put("Type", "ZIP Archive")
                    put("Compressed Size", FileUtils.formatSize(sizeBytes))
                    put("Files", fileCount.toString())
                    if (dirCount > 0) put("Folders", dirCount.toString())
                },
                isLoading = false
            )
        }
        ext in setOf("rar", "7z", "tar", "gz", "bz2", "xz") -> {
            ViewerState(
                mode = ViewerMode.ARCHIVE_INFO,
                fileInfo = mapOf(
                    "Type" to "${ext.uppercase()} Archive",
                    "Compressed Size" to FileUtils.formatSize(sizeBytes),
                    "Note" to "Cannot list contents of $ext format in-app"
                ),
                isLoading = false
            )
        }
        ext in setOf("apk", "xapk") -> {
            ViewerState(
                mode = ViewerMode.BINARY_INFO,
                fileInfo = mapOf("Type" to "Android Package"),
                isLoading = false
            )
        }
        ext == "pdf" -> {
            val pageCount = runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { it.pageCount }
                }
            }.getOrElse { 0 }
            ViewerState(
                mode = ViewerMode.PDF,
                pdfPageCount = pageCount,
                fileInfo = mapOf(
                    "Type" to "PDF Document",
                    "Pages" to pageCount.toString(),
                    "Size" to FileUtils.formatSize(sizeBytes)
                ),
                isLoading = false
            )
        }
        ext in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp") -> {
            ViewerState(
                mode = ViewerMode.BINARY_INFO,
                fileInfo = mapOf("Type" to "Office Document", "Note" to "Use 'Open With' to view this file"),
                isLoading = false
            )
        }
        sizeBytes > 5 * 1024 * 1024 -> {
            ViewerState(isLoading = false, error = "File is too large to preview (${FileUtils.formatSize(sizeBytes)}). Use 'Open With' to view it.")
        }
        ext == "json" -> {
            runCatching {
                val text = file.readText(Charsets.UTF_8)
                val formatted = try {
                    when {
                        text.trimStart().startsWith("{") -> JSONObject(text).toString(2)
                        text.trimStart().startsWith("[") -> JSONArray(text).toString(2)
                        else -> text
                    }
                } catch (_: Exception) { text }
                ViewerState(mode = ViewerMode.JSON, lines = formatted.lines(), isLoading = false)
            }.getOrElse {
                ViewerState(isLoading = false, error = "Failed to parse JSON: ${it.message}")
            }
        }
        else -> {
            runCatching {
                val text = file.readText(Charsets.UTF_8)
                ViewerState(mode = ViewerMode.TEXT, lines = text.lines(), isLoading = false)
            }.getOrElse {
                runCatching {
                    val text = file.readText(Charsets.ISO_8859_1)
                    ViewerState(mode = ViewerMode.TEXT, lines = text.lines(), isLoading = false)
                }.getOrElse {
                    ViewerState(
                        mode = ViewerMode.BINARY_INFO,
                        fileInfo = mapOf("Type" to "Binary / Unknown format", "Note" to "Cannot display binary content"),
                        isLoading = false
                    )
                }
            }
        }
    }
}
