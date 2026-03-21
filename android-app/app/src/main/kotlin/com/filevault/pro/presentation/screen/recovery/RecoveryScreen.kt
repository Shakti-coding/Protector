package com.filevault.pro.presentation.screen.recovery

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.filevault.pro.presentation.screen.photos.EmptyState
import com.filevault.pro.presentation.screen.photos.MultiSelectActionBar
import com.filevault.pro.presentation.screen.photos.SearchBar
import com.filevault.pro.util.FileUtils
import com.filevault.pro.util.gridScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecoveryScreen(
    viewModel: RecoveryViewModel = hiltViewModel(),
    onFileClick: (path: String, mimeType: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isMultiSelect = selectedIds.isNotEmpty()
    var isBusy by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    var isHeaderCollapsed by remember { mutableStateOf(false) }

    val displayedFiles = remember(uiState.foundFiles, uiState.filterType, uiState.searchQuery) {
        viewModel.filteredFiles()
    }

    val recoveryFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                isBusy = true
                val folder = DocumentFile.fromTreeUri(context, uri) ?: return@launch
                selectedIds.forEach { id ->
                    val rf = displayedFiles.find { it.id == id } ?: return@forEach
                    val src = File(rf.path)
                    if (src.exists()) {
                        val mime = rf.mimeType.ifBlank { "*/*" }
                        val dest = folder.createFile(mime, src.name) ?: return@forEach
                        context.contentResolver.openOutputStream(dest.uri)?.use { out ->
                            src.inputStream().use { it.copyTo(out) }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    selectedIds = emptySet()
                    isBusy = false
                }
            }
        }
    }

    fun shareSelected() {
        val uris = selectedIds.mapNotNull { id ->
            val rf = displayedFiles.find { it.id == id } ?: return@mapNotNull null
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(rf.path))
            }.getOrNull()
        }
        if (uris.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${selectedIds.size} file(s)"))
    }

    fun zipAndShare() {
        scope.launch(Dispatchers.IO) {
            isBusy = true
            try {
                val zipFile = File(context.cacheDir, "recovery_export_${System.currentTimeMillis()}.zip")
                ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                    selectedIds.forEach { id ->
                        val rf = displayedFiles.find { it.id == id } ?: return@forEach
                        val f = File(rf.path)
                        if (f.exists()) {
                            zos.putNextEntry(ZipEntry(f.name))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, "Share ZIP"))
                }
            } finally {
                isBusy = false
            }
        }
    }

    fun saveToDownloads() {
        scope.launch(Dispatchers.IO) {
            isBusy = true
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            var copied = 0
            selectedIds.forEach { id ->
                val rf = displayedFiles.find { it.id == id } ?: return@forEach
                val src = File(rf.path)
                if (src.exists()) {
                    var dest = File(downloadsDir, src.name)
                    var counter = 1
                    while (dest.exists()) {
                        dest = File(downloadsDir, "${src.nameWithoutExtension}($counter).${src.extension}")
                        counter++
                    }
                    runCatching { src.copyTo(dest, overwrite = false) }
                    copied++
                }
            }
            val count = copied
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "$count file(s) saved to Downloads", Toast.LENGTH_SHORT).show()
                selectedIds = emptySet()
                isBusy = false
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (isMultiSelect)
                            Text("${selectedIds.size} selected", fontWeight = FontWeight.SemiBold)
                        else
                            Text("Recovery", fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        if (isMultiSelect) {
                            IconButton(onClick = { selectedIds = displayedFiles.map { it.id }.toSet() }) {
                                Icon(Icons.Default.SelectAll, "Select All")
                            }
                            IconButton(onClick = { selectedIds = emptySet() }) {
                                Icon(Icons.Default.Close, "Deselect")
                            }
                        } else {
                            IconButton(onClick = { isHeaderCollapsed = !isHeaderCollapsed }) {
                                Icon(
                                    if (isHeaderCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                    if (isHeaderCollapsed) "Expand filters" else "Collapse filters"
                                )
                            }
                            IconButton(onClick = viewModel::toggleView) {
                                Icon(
                                    if (uiState.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    "Toggle view"
                                )
                            }
                        }
                    }
                )

                if (!isMultiSelect) {
                    SearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                        placeholder = "Search recovered files…"
                    )

                    AnimatedVisibility(
                        visible = !isHeaderCollapsed,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            ShizukuStatusBar(
                                status = uiState.shizukuStatus,
                                onAction = {
                                    when (uiState.shizukuStatus) {
                                        ShizukuStatus.NOT_INSTALLED -> {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            }.getOrElse {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            }
                                        }
                                        ShizukuStatus.NOT_RUNNING -> {
                                            runCatching {
                                                context.startActivity(
                                                    context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            }
                                            viewModel.refreshShizukuStatus()
                                        }
                                        ShizukuStatus.NO_PERMISSION -> viewModel.requestShizukuPermission()
                                        ShizukuStatus.READY -> viewModel.refreshShizukuStatus()
                                    }
                                }
                            )

                            ScanControlBar(
                                uiState = uiState,
                                onScanModeChange = viewModel::setScanMode,
                                onVolumeClick = { showVolumeSheet = true },
                                onStartScan = viewModel::startScan,
                                onPauseScan = viewModel::pauseScan,
                                onStopScan = viewModel::stopScan
                            )
                        }
                    }

                    RecoveryFileCountRow(
                        columns = uiState.gridColumns,
                        onChange = viewModel::setGridColumns,
                        itemCount = displayedFiles.size,
                        isGridView = uiState.isGridView
                    )

                    TypeFilterChips(
                        selected = uiState.filterType,
                        onSelect = viewModel::setFilterType,
                        counts = computeTypeCounts(uiState.foundFiles)
                    )
                }
            }
        },
        bottomBar = {
            if (isMultiSelect) {
                MultiSelectActionBar(
                    selectedCount = selectedIds.size,
                    isBusy = isBusy,
                    onShare = ::shareSelected,
                    onZip = ::zipAndShare,
                    onSaveToFolder = { recoveryFolderLauncher.launch(null) },
                    onSaveToDownloads = ::saveToDownloads,
                    onDeleteFromApp = { showDeleteConfirm = true },
                    onClearSelection = { selectedIds = emptySet() }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                uiState.scanState == ScanState.SCANNING -> {
                    ScanProgressOverlay(uiState = uiState)
                }
                uiState.errorMessage != null && uiState.scanState == ScanState.DONE && displayedFiles.isEmpty() -> {
                    ErrorState(message = uiState.errorMessage!!)
                }
                displayedFiles.isEmpty() && uiState.scanState == ScanState.DONE -> {
                    EmptyState(
                        message = "No recoverable files found.\nTry Deep Scan for more results.",
                        icon = Icons.Default.SearchOff
                    )
                }
                displayedFiles.isEmpty() -> {
                    EmptyState(
                        message = "Start a scan to find recoverable files.\nQuick Scan works without any special permissions.",
                        icon = Icons.Default.Restore
                    )
                }
                uiState.isGridView -> {
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(uiState.gridColumns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().gridScrollbar(gridState),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(displayedFiles, key = { it.id }) { rf ->
                            RecoveryGridItem(
                                file = rf,
                                isSelected = rf.id in selectedIds,
                                onClick = {
                                    if (isMultiSelect) {
                                        selectedIds = if (rf.id in selectedIds)
                                            selectedIds - rf.id else selectedIds + rf.id
                                    } else {
                                        onFileClick(rf.path, rf.mimeType)
                                    }
                                },
                                onLongClick = { selectedIds = selectedIds + rf.id }
                            )
                        }
                    }
                }
                else -> {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(displayedFiles, key = { it.id }) { rf ->
                            RecoveryListItem(
                                file = rf,
                                isSelected = rf.id in selectedIds,
                                onClick = {
                                    if (isMultiSelect) {
                                        selectedIds = if (rf.id in selectedIds)
                                            selectedIds - rf.id else selectedIds + rf.id
                                    } else {
                                        onFileClick(rf.path, rf.mimeType)
                                    }
                                },
                                onLongClick = { selectedIds = selectedIds + rf.id }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove from Results") },
            text = { Text("Remove ${selectedIds.size} file(s) from the recovery results list?") },
            confirmButton = {
                TextButton(onClick = {
                    selectedIds = emptySet()
                    showDeleteConfirm = false
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showVolumeSheet) {
        ModalBottomSheet(onDismissRequest = { showVolumeSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Storage Volume", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                uiState.availableVolumes.forEach { vol ->
                    ListItem(
                        headlineContent = { Text(vol) },
                        leadingContent = {
                            Icon(
                                if (vol.contains("SD")) Icons.Default.SdCard else Icons.Default.PhoneAndroid,
                                null
                            )
                        },
                        trailingContent = {
                            if (vol == uiState.selectedVolume)
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setVolume(vol)
                                showVolumeSheet = false
                            }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun computeTypeCounts(files: List<RecoveredFile>): Map<RecoveryFileType, Int> {
    return files.groupBy { it.fileType }.mapValues { it.value.size }
}

@Composable
private fun RecoveryFileCountRow(
    columns: Int,
    onChange: (Int) -> Unit,
    itemCount: Int,
    isGridView: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$itemCount file${if (itemCount != 1) "s" else ""} found",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            modifier = Modifier.weight(1f)
        )
        if (isGridView) {
            listOf(2, 3, 4).forEach { count ->
                IconButton(
                    onClick = { onChange(count) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        when (count) {
                            2 -> Icons.Default.GridView
                            3 -> Icons.Default.Apps
                            else -> Icons.Default.GridOn
                        },
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = if (columns == count) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(0.4f)
                    )
                }
            }
        }
    }
}

private data class ShizukuBarState(
    val bgColor: Color,
    val icon: ImageVector,
    val label: String,
    val actionLabel: String,
    val showAction: Boolean
)

@Composable
private fun shizukuBarState(status: ShizukuStatus): ShizukuBarState {
    val errorBg = MaterialTheme.colorScheme.errorContainer
    val tertiaryBg = MaterialTheme.colorScheme.tertiaryContainer
    val secondaryBg = MaterialTheme.colorScheme.secondaryContainer
    return when (status) {
        ShizukuStatus.NOT_INSTALLED -> ShizukuBarState(
            bgColor = errorBg,
            icon = Icons.Default.Warning,
            label = "Shizuku not installed — Deep Scan unavailable",
            actionLabel = "Install",
            showAction = true
        )
        ShizukuStatus.NOT_RUNNING -> ShizukuBarState(
            bgColor = tertiaryBg,
            icon = Icons.Default.PowerSettingsNew,
            label = "Shizuku installed but not running",
            actionLabel = "Open Shizuku",
            showAction = true
        )
        ShizukuStatus.NO_PERMISSION -> ShizukuBarState(
            bgColor = secondaryBg,
            icon = Icons.Default.Lock,
            label = "Shizuku running — grant permission",
            actionLabel = "Grant",
            showAction = true
        )
        ShizukuStatus.READY -> ShizukuBarState(
            bgColor = Color(0xFF1B5E20).copy(alpha = 0.15f),
            icon = Icons.Default.CheckCircle,
            label = "Shizuku ready — Deep Scan available",
            actionLabel = "",
            showAction = false
        )
    }
}

@Composable
private fun ShizukuStatusBar(
    status: ShizukuStatus,
    onAction: () -> Unit
) {
    val state = shizukuBarState(status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(state.bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            state.icon, null,
            modifier = Modifier.size(16.dp),
            tint = if (status == ShizukuStatus.READY) Color(0xFF2E7D32)
                   else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(state.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                when (status) {
                    ShizukuStatus.NOT_INSTALLED -> "Required for deep block-level scanning"
                    ShizukuStatus.NOT_RUNNING   -> "Start Shizuku via ADB or root, then tap Open"
                    ShizukuStatus.NO_PERMISSION -> "Allow FileVault Pro in Shizuku"
                    ShizukuStatus.READY         -> "Deep scan unlocked · block-level carving available"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        if (state.showAction) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(state.actionLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScanControlBar(
    uiState: RecoveryUiState,
    onScanModeChange: (ScanMode) -> Unit,
    onVolumeClick: () -> Unit,
    onStartScan: () -> Unit,
    onPauseScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScanModeChip(
                label = "Quick Scan",
                selected = uiState.scanMode == ScanMode.QUICK,
                onClick = { onScanModeChange(ScanMode.QUICK) }
            )
            Spacer(Modifier.width(8.dp))
            ScanModeChip(
                label = "Deep Scan",
                selected = uiState.scanMode == ScanMode.DEEP,
                enabled = uiState.shizukuStatus == ShizukuStatus.READY,
                onClick = { onScanModeChange(ScanMode.DEEP) }
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onVolumeClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Storage, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(uiState.selectedVolume, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            when (uiState.scanState) {
                ScanState.IDLE, ScanState.DONE -> {
                    Button(
                        onClick = onStartScan,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.scanState == ScanState.DONE) "Scan Again" else "Start Scan")
                    }
                }
                ScanState.SCANNING -> {
                    OutlinedButton(onClick = onPauseScan, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Pause, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pause")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onStopScan, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Stop")
                    }
                }
                ScanState.PAUSED -> {
                    Button(onClick = onStartScan, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Resume")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onStopScan, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        enabled = enabled,
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
        } else null
    )
}

@Composable
private fun TypeFilterChips(
    selected: RecoveryFileType,
    onSelect: (RecoveryFileType) -> Unit,
    counts: Map<RecoveryFileType, Int>
) {
    val types = listOf(
        RecoveryFileType.ALL to "All",
        RecoveryFileType.PHOTO to "Photos",
        RecoveryFileType.VIDEO to "Videos",
        RecoveryFileType.AUDIO to "Audio",
        RecoveryFileType.DOCUMENT to "Docs",
        RecoveryFileType.ARCHIVE to "Archives"
    )
    val totalCount = counts.values.sum()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(types) { (type, label) ->
            val count = if (type == RecoveryFileType.ALL) totalCount else counts[type] ?: 0
            val chipLabel = if (count > 0) "$label ($count)" else label
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(chipLabel, style = MaterialTheme.typography.labelSmall) },
                leadingIcon = if (selected == type) {
                    { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun ScanProgressOverlay(uiState: RecoveryUiState) {
    val pct = (uiState.progress * 100).toInt()
    val isDeep = uiState.scanMode == ScanMode.DEEP
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(88.dp),
                strokeWidth = 6.dp,
                progress = { uiState.progress }
            )
            Text(
                "$pct%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            uiState.scanPhase.ifBlank { if (isDeep) "Deep scanning…" else "Quick scanning…" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { uiState.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )

        Spacer(Modifier.height(16.dp))

        if (uiState.currentPath.isNotBlank()) {
            Text(
                uiState.currentPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatChip(
                icon = Icons.Default.InsertDriveFile,
                label = "${uiState.foundFiles.size}",
                sub = "Found"
            )
            if (isDeep && uiState.totalBytes > 0) {
                StatChip(
                    icon = Icons.Default.Storage,
                    label = formatBytes(uiState.scannedBytes),
                    sub = "of ${formatBytes(uiState.totalBytes)}"
                )
                StatChip(
                    icon = Icons.Default.ViewModule,
                    label = "${uiState.blocksScanned}",
                    sub = "Blocks"
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024L -> "${bytes} B"
        bytes < 1024L * 1024L -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024L * 1024L * 1024L -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}

@Composable
private fun StatChip(icon: ImageVector, label: String, sub: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(
                Icons.Default.ErrorOutline, null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error.copy(0.7f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecoveryGridItem(
    file: RecoveredFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (file.fileType == RecoveryFileType.PHOTO || file.fileType == RecoveryFileType.VIDEO) {
            AsyncImage(
                model = File(file.path),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val (icon, color) = fileTypeIconAndColor(file.fileType, file.name)
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        icon, null,
                        modifier = Modifier.size(32.dp),
                        tint = color
                    )
                    val ext = file.name.substringAfterLast('.', "").uppercase()
                    if (ext.isNotEmpty() && ext.length <= 5) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            ext,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        if (file.fileType == RecoveryFileType.VIDEO) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    .size(18.dp).clip(CircleShape)
                    .background(Color.Black.copy(0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }

        ProbabilityBadge(probability = file.recoveryProbability, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))

        if (isSelected) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecoveryListItem(
    file: RecoveredFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateStr = remember(file.lastModified) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(file.lastModified))
    }
    val (icon, iconColor) = fileTypeIconAndColor(file.fileType, file.name)
    val ext = file.name.substringAfterLast('.', "").uppercase()

    ListItem(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(0.3f)
                else Color.Transparent
            ),
        leadingContent = {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (file.fileType == RecoveryFileType.PHOTO || file.fileType == RecoveryFileType.VIDEO) {
                    AsyncImage(
                        model = File(file.path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                    )
                    if (file.fileType == RecoveryFileType.VIDEO) {
                        Icon(Icons.Default.PlayCircle, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(22.dp))
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
                        if (ext.isNotEmpty() && ext.length <= 5) {
                            Text(ext, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                fontWeight = FontWeight.Bold, color = iconColor, maxLines = 1)
                        }
                    }
                }
                if (isSelected) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(0.45f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        headlineContent = {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    FileUtils.formatSize(file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
                Text(" · ", color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                Text(
                    dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
                Text(" · ", color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                Text(
                    file.source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(0.7f)
                )
            }
        },
        trailingContent = {
            ProbabilityBadge(probability = file.recoveryProbability)
        }
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
}

@Composable
private fun ProbabilityBadge(probability: Int, modifier: Modifier = Modifier) {
    val color = when {
        probability >= 85 -> Color(0xFF2E7D32)
        probability >= 60 -> Color(0xFFF57F17)
        else -> Color(0xFFC62828)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(0.85f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            "$probability%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

private data class IconAndColor(val icon: ImageVector, val color: Color)

@Composable
private fun fileTypeIconAndColor(type: RecoveryFileType, fileName: String): IconAndColor {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (type) {
        RecoveryFileType.PHOTO -> IconAndColor(Icons.Default.Image, MaterialTheme.colorScheme.tertiary)
        RecoveryFileType.VIDEO -> IconAndColor(Icons.Default.Movie, Color(0xFF9C27B0))
        RecoveryFileType.AUDIO -> IconAndColor(Icons.Default.MusicNote, Color(0xFF2196F3))
        RecoveryFileType.ARCHIVE -> IconAndColor(Icons.Default.FolderZip, Color(0xFFFF9800))
        RecoveryFileType.DOCUMENT -> {
            when (ext) {
                "apk" -> IconAndColor(Icons.Default.Android, Color(0xFF4CAF50))
                "pdf" -> IconAndColor(Icons.Default.PictureAsPdf, Color(0xFFE53935))
                "json", "xml", "yaml", "yml" -> IconAndColor(Icons.Default.Code, Color(0xFF607D8B))
                "doc", "docx" -> IconAndColor(Icons.Default.Description, Color(0xFF1565C0))
                "xls", "xlsx" -> IconAndColor(Icons.Default.GridOn, Color(0xFF2E7D32))
                "ppt", "pptx" -> IconAndColor(Icons.Default.Slideshow, Color(0xFFE65100))
                "txt", "log" -> IconAndColor(Icons.Default.Article, Color(0xFF546E7A))
                "html", "htm" -> IconAndColor(Icons.Default.Web, Color(0xFFE91E63))
                else -> IconAndColor(Icons.Default.Description, MaterialTheme.colorScheme.primary)
            }
        }
        RecoveryFileType.ALL -> IconAndColor(Icons.Default.InsertDriveFile, MaterialTheme.colorScheme.onSurface.copy(0.6f))
    }
}
