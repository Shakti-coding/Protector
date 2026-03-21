package com.filevault.pro.presentation.screen.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.WorkInfo
import com.filevault.pro.domain.model.SyncHistory
import com.filevault.pro.domain.model.SyncProfile
import com.filevault.pro.domain.model.SyncStatus
import com.filevault.pro.domain.model.SyncType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    var logsProfileId by remember { mutableStateOf<Long?>(null) }

    val syncingProfileIds = remember { mutableStateMapOf<Long, Boolean>() }

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
                        viewModel = viewModel,
                        onEdit = { onEditProfile(profile.id) },
                        onDelete = { deleteTarget = profile },
                        onToggleActive = { viewModel.setProfileActive(profile.id, !profile.isActive) },
                        onViewHistory = { onViewHistory(profile.id) },
                        onSyncNow = {
                            syncingProfileIds[profile.id] = true
                            viewModel.syncNow(profile.id)
                            snackMessage = "Sync started for \"${profile.name}\""
                        },
                        onViewLogs = { logsProfileId = profile.id }
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

    logsProfileId?.let { pid ->
        val profileName = profiles.find { it.id == pid }?.name ?: "Profile"
        val history by viewModel.getHistoryForProfile(pid).collectAsState()
        SyncLogsBottomSheet(
            profileName = profileName,
            history = history,
            onDismiss = { logsProfileId = null }
        )
    }
}

private fun buildFullLogText(profileName: String, history: List<SyncHistory>): String {
    val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val sb = StringBuilder()
    sb.appendLine("=== FileVault Pro — Sync Log Terminal ===")
    sb.appendLine("Profile : $profileName")
    sb.appendLine("Exported: ${dateFmt.format(Date())}")
    sb.appendLine("Total runs: ${history.size}")
    sb.appendLine("==========================================")
    sb.appendLine()
    history.forEachIndexed { idx, h ->
        val statusSymbol = when (h.status) {
            SyncStatus.SUCCESS    -> "✓"
            SyncStatus.PARTIAL    -> "~"
            SyncStatus.FAILED     -> "✗"
            SyncStatus.IN_PROGRESS -> "●"
        }
        val startStr = dateFmt.format(Date(h.startedAt))
        val durationStr = h.completedAt?.let { end ->
            val ms = end - h.startedAt
            when {
                ms < 1000    -> "${ms}ms"
                ms < 60_000  -> "${ms / 1000}s"
                else         -> "${TimeUnit.MILLISECONDS.toMinutes(ms)}m ${(ms / 1000) % 60}s"
            }
        } ?: "in progress"

        sb.appendLine("--- Run #${idx + 1} ---")
        sb.appendLine("[$statusSymbol] ${h.status.name}  |  $startStr  |  duration: $durationStr")
        sb.appendLine("Synced : ${h.filesSynced}  |  Failed: ${h.filesFailed}")
        if (!h.errorMessage.isNullOrBlank()) {
            sb.appendLine("ERROR  : ${h.errorMessage}")
        }
        if (h.status == SyncStatus.SUCCESS && h.filesSynced == 0) {
            sb.appendLine("INFO   : No new files to send. All files already synced.")
        }
        sb.appendLine()
    }
    sb.append("==========================================")
    return sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncLogsBottomSheet(
    profileName: String,
    history: List<SyncHistory>,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D1117)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FF7F).copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Terminal, null,
                        modifier = Modifier.size(16.dp), tint = Color(0xFF00FF7F))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Sync Log Terminal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF7F),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        profileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    "${history.size} run${if (history.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8B949E),
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val logText = buildFullLogText(profileName, history)
                        clipboard.setText(AnnotatedString(logText))
                        copied = true
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (copied) Color(0xFF00FF7F).copy(0.15f)
                            else Color(0xFF21262D)
                        )
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = if (copied) "Copied!" else "Copy all logs",
                        modifier = Modifier.size(16.dp),
                        tint = if (copied) Color(0xFF00FF7F) else Color(0xFF8B949E)
                    )
                }
            }

            AnimatedVisibility(visible = copied) {
                Text(
                    "  ✓ Copied to clipboard",
                    color = Color(0xFF00FF7F),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            HorizontalDivider(color = Color(0xFF21262D))

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$ no sync runs yet",
                            color = Color(0xFF8B949E),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Run a sync to see detailed logs here.",
                            color = Color(0xFF8B949E).copy(0.5f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    history.forEach { entry ->
                        SyncLogEntry(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncLogEntry(history: SyncHistory) {
    val (statusColor, statusSymbol, statusLabel) = when (history.status) {
        SyncStatus.SUCCESS -> Triple(Color(0xFF3FB950), "✓", "SUCCESS")
        SyncStatus.PARTIAL -> Triple(Color(0xFFD29922), "~", "PARTIAL")
        SyncStatus.FAILED  -> Triple(Color(0xFFF85149), "✗", "FAILED")
        SyncStatus.IN_PROGRESS -> Triple(Color(0xFF58A6FF), "●", "RUNNING")
    }

    val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val startStr = dateFmt.format(Date(history.startedAt))
    val durationStr = history.completedAt?.let { end ->
        val ms = end - history.startedAt
        when {
            ms < 1000 -> "${ms}ms"
            ms < 60_000 -> "${ms / 1000}s"
            else -> "${TimeUnit.MILLISECONDS.toMinutes(ms)}m ${(ms / 1000) % 60}s"
        }
    } ?: "in progress…"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF161B22))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                statusSymbol,
                color = statusColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                statusLabel,
                color = statusColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                startStr,
                color = Color(0xFF8B949E),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                durationStr,
                color = Color(0xFF58A6FF),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LogLine(label = "synced", value = "${history.filesSynced}", color = Color(0xFF3FB950))
            LogLine(label = "failed", value = "${history.filesFailed}", color = Color(0xFFF85149))
        }

        if (!history.errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = Color(0xFF21262D))
            Spacer(Modifier.height(6.dp))
            Text(
                "ERROR:",
                color = Color(0xFFF85149),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                history.errorMessage,
                color = Color(0xFFF85149).copy(0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            )
        }

        if (history.status == SyncStatus.SUCCESS && history.filesSynced == 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "→ No new files to send. All files already synced.",
                color = Color(0xFF8B949E),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun LogLine(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label:",
            color = Color(0xFF8B949E),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Spacer(Modifier.width(4.dp))
        Text(
            value,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
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
    viewModel: SyncViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit,
    onViewHistory: () -> Unit,
    onSyncNow: () -> Unit,
    onViewLogs: () -> Unit
) {
    val workInfoList by viewModel.getSyncWorkInfo(profile.id).collectAsState(emptyList())
    val isRunning = workInfoList.any {
        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
    }
    val lastWorkInfo = workInfoList.lastOrNull()
    val progressSynced = lastWorkInfo?.progress?.getInt(SyncWorker.KEY_PROGRESS_SYNCED, 0) ?: 0
    val progressTotal = lastWorkInfo?.progress?.getInt(SyncWorker.KEY_PROGRESS_TOTAL, 0) ?: 0
    val lastSucceeded = workInfoList.any { it.state == WorkInfo.State.SUCCEEDED }
    val lastFailed = workInfoList.any { it.state == WorkInfo.State.FAILED }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isRunning -> MaterialTheme.colorScheme.secondaryContainer.copy(0.3f)
                profile.isActive -> MaterialTheme.colorScheme.primaryContainer.copy(0.2f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
            }
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
                            append(if (profile.intervalHours == 0) "Manual only" else "Every ${profile.intervalHours}h")
                            if (profile.fileTypeScope.isNotEmpty()) {
                                append(" · ")
                                append(profile.fileTypeScope.joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } })
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                }
                Switch(checked = profile.isActive, onCheckedChange = { onToggleActive() })
            }

            if (profile.lastSyncAt != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Last sync: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(profile.lastSyncAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }

            AnimatedVisibility(
                visible = isRunning,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (progressTotal > 0) "Syncing $progressSynced / $progressTotal files…"
                            else "Sync in progress…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                    if (progressTotal > 0) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progressSynced.toFloat() / progressTotal.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            AnimatedVisibility(visible = lastSucceeded && !isRunning) {
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = Color(0xFF2E7D32))
                    Spacer(Modifier.width(4.dp))
                    Text("Last sync completed successfully", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                }
            }

            AnimatedVisibility(visible = lastFailed && !isRunning) {
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Last sync failed — tap Logs for details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { onViewLogs() }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSyncNow,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (profile.type == SyncType.TELEGRAM) Color(0xFF0088CC) else Color(0xFFEA4335)
                    )
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Syncing…")
                    } else {
                        Icon(Icons.Default.Sync, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync Now")
                    }
                }

                OutlinedButton(
                    onClick = onViewLogs,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF00C853)
                    )
                ) {
                    Icon(Icons.Default.Terminal, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Logs", style = MaterialTheme.typography.labelMedium)
                }

                IconButton(onClick = onViewHistory) {
                    Icon(Icons.Default.History, "History", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
