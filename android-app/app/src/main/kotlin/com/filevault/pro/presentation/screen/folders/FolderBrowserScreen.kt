package com.filevault.pro.presentation.screen.folders

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileType
import com.filevault.pro.domain.model.FolderInfo
import com.filevault.pro.domain.repository.FileRepository
import com.filevault.pro.util.FileUtils
import com.filevault.pro.util.MediaQueue
import com.filevault.pro.util.rememberFileThumbnail
import com.filevault.pro.util.simpleScrollbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class BrowseItem {
    data class Folder(val info: FolderInfo) : BrowseItem()
    data class FileItem(val entry: FileEntry) : BrowseItem()
}

@HiltViewModel
class FolderBrowserViewModel @Inject constructor(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath

    private val _rawItems = MutableStateFlow<List<BrowseItem>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val items: StateFlow<List<BrowseItem>> = combine(_rawItems, _searchQuery) { list, q ->
        if (q.isBlank()) list
        else list.filter { item ->
            when (item) {
                is BrowseItem.Folder -> item.info.name.contains(q, ignoreCase = true) ||
                        item.info.path.contains(q, ignoreCase = true)
                is BrowseItem.FileItem -> item.entry.name.contains(q, ignoreCase = true) ||
                        item.entry.path.contains(q, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pathStack = MutableStateFlow<List<String>>(emptyList())
    val pathStack: StateFlow<List<String>> = _pathStack

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    val folders: StateFlow<List<FolderInfo>> = fileRepository.getFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            folders.collect { folderList ->
                if (_currentPath.value == null) {
                    _rawItems.value = folderList.distinctBy { it.path }.map { BrowseItem.Folder(it) }
                    _isLoading.value = false
                }
            }
        }
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun navigateIntoFolder(folderPath: String) {
        _searchQuery.value = ""
        viewModelScope.launch {
            _isLoading.value = true
            _pathStack.value = _pathStack.value + (folderPath)
            _currentPath.value = folderPath
            loadFolderContents(folderPath)
        }
    }

    fun navigateUp(): Boolean {
        val stack = _pathStack.value
        return if (stack.isEmpty()) {
            false
        } else {
            _searchQuery.value = ""
            val newStack = stack.dropLast(1)
            _pathStack.value = newStack
            val parentPath = newStack.lastOrNull()
            _currentPath.value = parentPath
            viewModelScope.launch {
                _isLoading.value = true
                if (parentPath == null) {
                    val folderList = folders.value
                    _rawItems.value = folderList.distinctBy { it.path }.map { BrowseItem.Folder(it) }
                    _isLoading.value = false
                } else {
                    loadFolderContents(parentPath)
                }
            }
            true
        }
    }

    private suspend fun loadFolderContents(folderPath: String) {
        val dir = File(folderPath)
        if (!dir.exists() || !dir.isDirectory) {
            _isLoading.value = false
            return
        }

        val fsItems = dir.listFiles()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        ) ?: emptyList()

        val browseItems = fsItems.map { f ->
            if (f.isDirectory) {
                val subCount = f.listFiles()?.size ?: 0
                BrowseItem.Folder(
                    FolderInfo(
                        path = f.absolutePath,
                        name = f.name,
                        fileCount = subCount,
                        totalSizeBytes = 0L,
                        lastModified = f.lastModified()
                    )
                )
            } else {
                val ext = f.extension.lowercase()
                val fileType = FileType.fromExtension(ext)
                BrowseItem.FileItem(
                    FileEntry(
                        path = f.absolutePath,
                        name = f.name,
                        folderPath = f.parent ?: "",
                        folderName = f.parentFile?.name ?: "",
                        sizeBytes = f.length(),
                        lastModified = f.lastModified(),
                        mimeType = getMime(ext),
                        fileType = fileType,
                        dateAdded = f.lastModified()
                    )
                )
            }
        }

        _rawItems.value = browseItems
        _isLoading.value = false
    }
}

private fun getMime(ext: String) = when (ext) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "flac" -> "audio/flac"
    "pdf" -> "application/pdf"
    "json" -> "application/json"
    "txt", "md", "log" -> "text/plain"
    "xml" -> "application/xml"
    "zip" -> "application/zip"
    "apk" -> "application/vnd.android.package-archive"
    else -> "*/*"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBrowserScreen(
    viewModel: FolderBrowserViewModel = hiltViewModel(),
    onFileClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val pathStack by viewModel.pathStack.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    var showSearch by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<FileEntry?>(null) }
    var selectedFolder by remember { mutableStateOf<FolderInfo?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler(enabled = pathStack.isNotEmpty()) {
        viewModel.navigateUp()
    }

    if (selectedFile != null) {
        FileDetailSheet(
            file = selectedFile!!,
            sheetState = sheetState,
            onDismiss = { selectedFile = null },
            onOpen = { onFileClick(selectedFile!!.path); selectedFile = null },
            context = context
        )
    }

    if (selectedFolder != null) {
        FolderDetailSheet(
            folder = selectedFolder!!,
            sheetState = sheetState,
            onDismiss = { selectedFolder = null },
            onOpen = { viewModel.navigateIntoFolder(selectedFolder!!.path); selectedFolder = null },
            context = context
        )
    }

    Scaffold(
        topBar = {
            if (showSearch) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onClose = { showSearch = false; viewModel.setSearchQuery("") }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (currentPath == null) "Browse" else File(currentPath!!).name,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentPath != null) {
                                Text(
                                    currentPath!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (!viewModel.navigateUp()) onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.FolderOpen,
                                null, Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.2f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (searchQuery.isNotBlank()) "No results for \"$searchQuery\""
                                else "Empty folder",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                            )
                        }
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    val firstVisible = listState.firstVisibleItemIndex
                    val visibleCount = listState.layoutInfo.visibleItemsInfo.size
                    val totalItems = items.size
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .simpleScrollbar(listState)
                        ) {
                            items(items, key = {
                                when (it) {
                                    is BrowseItem.Folder -> "folder:${it.info.path}"
                                    is BrowseItem.FileItem -> "file:${it.entry.path}"
                                }
                            }) { item ->
                                when (item) {
                                    is BrowseItem.Folder -> {
                                        FolderItemRow(
                                            folder = item.info,
                                            onClick = { viewModel.navigateIntoFolder(item.info.path) },
                                            onLongClick = { selectedFolder = item.info }
                                        )
                                    }
                                    is BrowseItem.FileItem -> {
                                        FileItemRow(
                                            file = item.entry,
                                            onClick = {
                                                val entry = item.entry
                                                if (entry.fileType == FileType.PHOTO) {
                                                    val photoPaths = items
                                                        .filterIsInstance<BrowseItem.FileItem>()
                                                        .filter { it.entry.fileType == FileType.PHOTO }
                                                        .map { it.entry.path }
                                                    MediaQueue.set(entry.path, photoPaths)
                                                } else {
                                                    MediaQueue.set(entry.path, listOf(entry.path))
                                                }
                                                onFileClick(entry.path)
                                            },
                                            onLongClick = { selectedFile = item.entry }
                                        )
                                    }
                                }
                            }
                        }
                        if (totalItems > 0 && listState.isScrollInProgress) {
                            val end = (firstVisible + visibleCount).coerceAtMost(totalItems)
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 14.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
                                tonalElevation = 4.dp
                            ) {
                                Text(
                                    "${firstVisible + 1}–$end / $totalItems",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search files & folders…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, "Clear")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileDetailSheet(
    file: FileEntry,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    context: Context
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = fileTypeColor(file.fileType).copy(0.15f)
                ) {
                    Text(
                        file.fileType.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = fileTypeColor(file.fileType),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            DetailRow(Icons.Default.FolderOpen, "Location", file.folderPath)
            DetailRow(Icons.Default.Storage, "Size", FileUtils.formatSize(file.sizeBytes))
            DetailRow(Icons.Default.CalendarToday, "Modified", formatDetailDate(file.lastModified))
            DetailRow(Icons.Default.Description, "MIME", file.mimeType ?: "Unknown")
            if (file.lastSyncedAt != null) {
                DetailRow(Icons.Default.CloudDone, "Last synced", formatDetailDate(file.lastSyncedAt))
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open")
                }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", File(file.path)
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = file.mimeType ?: "*/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("File path", file.path))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CopyAll, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy path")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderDetailSheet(
    folder: FolderInfo,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    context: Context
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            DetailRow(Icons.Default.FolderOpen, "Path", folder.path)
            DetailRow(Icons.Default.InsertDriveFile, "Items", "${folder.fileCount}")
            if (folder.totalSizeBytes > 0) {
                DetailRow(Icons.Default.Storage, "Total size", FileUtils.formatSize(folder.totalSizeBytes))
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open")
                }
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Folder path", folder.path))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CopyAll, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy path")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            Text(value, style = MaterialTheme.typography.bodySmall,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.2f))
}

@Composable
private fun FolderItemRow(folder: FolderInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(folder.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                "${folder.fileCount} items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
        },
        leadingContent = {
            Icon(Icons.Default.Folder, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLongClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, "Details", Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                }
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
    )
}

@Composable
private fun FileItemRow(file: FileEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val thumbnail = rememberFileThumbnail(file, context)
    val hasThumbnail = thumbnail != null &&
            (file.fileType == FileType.PHOTO || file.fileType == FileType.VIDEO)

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = fileTypeColor(file.fileType).copy(0.15f)
                ) {
                    Text(
                        file.fileType.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = fileTypeColor(file.fileType),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    FileUtils.formatSize(file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasThumbnail) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (file.fileType == FileType.VIDEO) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = androidx.compose.ui.graphics.Color.Black.copy(0.45f)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    null,
                                    Modifier.size(18.dp).padding(2.dp),
                                    tint = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = fileTypeColor(file.fileType).copy(0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                fileTypeIcon(file.fileType),
                                null,
                                tint = fileTypeColor(file.fileType),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onLongClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, "Details", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(0.2f)
    )
}

private fun fileTypeIcon(type: FileType): ImageVector = when (type) {
    FileType.PHOTO -> Icons.Default.Image
    FileType.VIDEO -> Icons.Default.VideoFile
    FileType.AUDIO -> Icons.Default.AudioFile
    FileType.DOCUMENT -> Icons.Default.Description
    FileType.ARCHIVE -> Icons.Default.FolderZip
    FileType.APK -> Icons.Default.Android
    FileType.OTHER -> Icons.Default.InsertDriveFile
}

private fun fileTypeColor(type: FileType) = when (type) {
    FileType.PHOTO -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    FileType.VIDEO -> androidx.compose.ui.graphics.Color(0xFF2196F3)
    FileType.AUDIO -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
    FileType.DOCUMENT -> androidx.compose.ui.graphics.Color(0xFFFF9800)
    FileType.ARCHIVE -> androidx.compose.ui.graphics.Color(0xFF795548)
    FileType.APK -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    FileType.OTHER -> androidx.compose.ui.graphics.Color(0xFF607D8B)
}

private fun formatDetailDate(ms: Long): String =
    SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault()).format(Date(ms))
