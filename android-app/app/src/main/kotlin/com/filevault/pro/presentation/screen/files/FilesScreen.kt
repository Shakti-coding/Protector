package com.filevault.pro.presentation.screen.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileType
import com.filevault.pro.presentation.screen.photos.EmptyState
import com.filevault.pro.presentation.screen.photos.SearchBar
import com.filevault.pro.presentation.screen.photos.SortBottomSheet
import com.filevault.pro.util.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilesScreen(
    viewModel: FilesViewModel = hiltViewModel(),
    onFileClick: (String) -> Unit,
    onFolderBrowse: () -> Unit
) {
    val files by viewModel.files.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val selectedTypes by viewModel.selectedTypes.collectAsState()
    val isGroupByFolder by viewModel.isGroupByFolder.collectAsState()
    var showSortSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Files", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onFolderBrowse) { Icon(Icons.Default.FolderOpen, "Browse folders") }
                        IconButton(onClick = viewModel::toggleGroupByFolder) {
                            Icon(if (isGroupByFolder) Icons.Default.List else Icons.Default.AccountTree, null)
                        }
                        IconButton(onClick = { showSortSheet = true }) { Icon(Icons.Default.Sort, null) }
                    }
                )
                SearchBar(searchQuery, viewModel::setSearchQuery, "Search files…")
                TypeFilterChips(selectedTypes, viewModel::toggleTypeFilter)
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (files.isEmpty()) EmptyState("No files found", Icons.Default.InsertDriveFile)
            else {
                if (isGroupByFolder) {
                    GroupedFileList(files = files, onFileClick = onFileClick)
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(files, key = { it.path }) { file ->
                            FileListItem(file = file, onClick = { onFileClick(file.path) })
                        }
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        SortBottomSheet(sortOrder, viewModel::setSortOrder) { showSortSheet = false }
    }
}

@Composable
private fun TypeFilterChips(selectedTypes: Set<FileType>, onToggle: (FileType) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(FileType.values()) { type ->
            val selected = type in selectedTypes
            FilterChip(
                selected = selected,
                onClick = { onToggle(type) },
                label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                leadingIcon = {
                    if (selected) Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                }
            )
        }
    }
}

@Composable
private fun GroupedFileList(files: List<FileEntry>, onFileClick: (String) -> Unit) {
    val grouped = files.groupBy { it.folderName }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        grouped.forEach { (folderName, folderFiles) ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(folderName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("${folderFiles.size}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                }
            }
            items(folderFiles, key = { it.path }) { file ->
                FileListItem(file, onClick = { onFileClick(file.path) }, indent = true)
            }
        }
    }
}

@Composable
fun FileListItem(file: FileEntry, onClick: () -> Unit, indent: Boolean = false) {
    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = if (indent) 16.dp else 0.dp),
        headlineContent = {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(
                "${FileUtils.formatSize(file.sizeBytes)} · ${formatDate(file.lastModified)} · ${file.folderName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(fileTypeColor(file.fileType).copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    file.name.substringAfterLast(".").uppercase().take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = fileTypeColor(file.fileType)
                )
            }
        },
        trailingContent = {
            if (file.lastSyncedAt != null) {
                Icon(Icons.Default.CloudDone, null, modifier = Modifier.size(16.dp),
                    tint = Color(0xFF00AA44))
            }
        }
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f),
        modifier = Modifier.padding(horizontal = 16.dp))
}

private fun fileTypeColor(type: FileType) = when (type) {
    FileType.PHOTO -> Color(0xFF1848C4)
    FileType.VIDEO -> Color(0xFF8B009C)
    FileType.AUDIO -> Color(0xFF006A4E)
    FileType.DOCUMENT -> Color(0xFFB94E00)
    FileType.ARCHIVE -> Color(0xFF6B4226)
    FileType.APK -> Color(0xFF007A5E)
    FileType.OTHER -> Color(0xFF555555)
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(ms))
