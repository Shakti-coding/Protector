package com.filevault.pro.presentation.screen.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filevault.pro.domain.model.SyncProfile
import com.filevault.pro.domain.model.SyncType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncProfilesScreen(
    viewModel: SyncViewModel = hiltViewModel(),
    onAddProfile: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onViewHistory: (Long) -> Unit,
    onBack: () -> Unit
) {
    val profiles by viewModel.profiles.collectAsState()
    val manifestSummary by viewModel.manifestSummary.collectAsState()
    var deleteTarget by remember { mutableStateOf<SyncProfile?>(null) }
    var snackMessage by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportManifest(it) { ok ->
            snackMessage = if (ok) "Manifest exported" else "Export failed"
        }}
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importManifest(it) { count ->
            snackMessage = "Imported $count entries from manifest"
        }}
    }

    val snackState = remember { SnackbarHostState() }
    LaunchedEffect(snackMessage) {
        snackMessage?.let {
            snackState.showSnackbar(it)
            snackMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackState) },
        topBar = {
            TopAppBar(
                title = { Text("Sync Profiles", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProfile,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Profile") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ManifestCard(
                    summary = manifestSummary,
                    onExport = { exportLauncher.launch("sync_manifest_${System.currentTimeMillis()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    onClear = { showClearConfirm = true }
                )
            }

            if (profiles.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Sync, null, Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                            Spacer(Modifier.height(16.dp))
                            Text("No sync profiles yet", style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            Spacer(Modifier.height(8.dp))
                            Text("Tap + to add a Telegram or Email sync profile",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                        }
                    }
                }
            } else {
                items(profiles, key = { it.id }) { profile ->
                    SyncProfileCard(
                        profile = profile,
                        onEdit = { onEditProfile(profile.id) },
                        onDelete = { deleteTarget = profile },
                        onToggleActive = { viewModel.setProfileActive(profile.id, !profile.isActive) },
                        onViewHistory = { onViewHistory(profile.id) },
                        onSyncNow = { viewModel.syncNow(profile.id) }
                    )
                }
            }
        }
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Profile") },
            text = { Text("Delete \"${profile.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteProfile(profile.id); deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear Manifest?") },
            text = { Text("This removes all records of previously synced files. Files may be re-sent on next sync. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearManifest(); showClearConfirm = false; snackMessage = "Manifest cleared" },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ManifestCard(
    summary: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sync Manifest", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )
            Text(
                "Tracks which files have been sent to avoid duplicate uploads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Upload, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export")
                }
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SyncProfileCard(
    profile: SyncProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit,
    onViewHistory: () -> Unit,
    onSyncNow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (profile.isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(0.2f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(
                            if (profile.type == SyncType.TELEGRAM) Color(0xFF0088CC).copy(0.2f)
                            else Color(0xFFEA4335).copy(0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (profile.type == SyncType.TELEGRAM) Icons.Default.Send else Icons.Default.Email,
                        null, modifier = Modifier.size(24.dp),
                        tint = if (profile.type == SyncType.TELEGRAM) Color(0xFF0088CC) else Color(0xFFEA4335)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(profile.type.name.lowercase().replaceFirstChar { it.uppercase() })
                            append(" · ")
                            append(if (profile.intervalHours == 0) "Manual" else "Every ${profile.intervalHours}h")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                }
                Switch(checked = profile.isActive, onCheckedChange = { onToggleActive() })
            }

            if (profile.lastSyncAt != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Last sync: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(profile.lastSyncAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSyncNow, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Sync, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sync Now")
                }
                IconButton(onClick = onViewHistory) { Icon(Icons.Default.History, null) }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
