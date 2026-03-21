package com.filevault.pro.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.filevault.pro.R
import com.filevault.pro.data.preferences.EncryptedPrefs
import com.filevault.pro.data.remote.email.EmailSyncImpl
import com.filevault.pro.data.remote.telegram.TelegramSyncImpl
import com.filevault.pro.data.sync.ManifestEntry
import com.filevault.pro.data.sync.SyncManifestManager
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.SyncHistory
import com.filevault.pro.domain.model.SyncProfile
import com.filevault.pro.domain.model.SyncStatus
import com.filevault.pro.domain.model.SyncType
import com.filevault.pro.domain.repository.FileRepository
import com.filevault.pro.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val fileRepository: FileRepository,
    private val telegramSync: TelegramSyncImpl,
    private val emailSync: EmailSyncImpl,
    private val encryptedPrefs: EncryptedPrefs,
    private val manifestManager: SyncManifestManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_FORCE_SYNC = "force_sync"
        const val KEY_PROGRESS_SYNCED = "progress_synced"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        private const val CHANNEL_ID = "sync_channel"
        private const val NOTIF_ID = 3001
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo("Syncing files…", 0, 0)

    override suspend fun doWork(): Result {
        val profileId = inputData.getLong(KEY_PROFILE_ID, -1)
        if (profileId == -1L) return Result.failure()

        val profile = syncRepository.getProfileById(profileId) ?: return Result.failure()
        val forceSync = inputData.getBoolean(KEY_FORCE_SYNC, false)
        if (!profile.isActive && !forceSync) return Result.success()

        Log.d(TAG, "Starting sync for profile: ${profile.name}")

        val historyId = syncRepository.insertHistory(
            SyncHistory(
                profileId = profile.id,
                profileName = profile.name,
                startedAt = System.currentTimeMillis(),
                status = SyncStatus.IN_PROGRESS
            )
        )

        runCatching {
            setForeground(buildForegroundInfo("Starting sync: ${profile.name}", 0, 0))
        }.onFailure { Log.w(TAG, "setForeground failed (non-fatal): ${it.message}") }

        val allFiles = fileRepository.getUnsyncedFiles(profile.fileTypeScope)
        val alreadySynced = manifestManager.getSyncedPaths()
        val files = allFiles.filter { it.path !in alreadySynced }
        val total = files.size
        Log.d(TAG, "Files to sync: $total (unsynced=${allFiles.size}, manifest-skipped=${allFiles.size - total}, scope=${profile.fileTypeScope})")

        if (total == 0) {
            syncRepository.updateHistoryCompletion(historyId, System.currentTimeMillis(), 0, 0, SyncStatus.SUCCESS, null)
            syncRepository.updateLastSyncAt(profileId, System.currentTimeMillis())
            runCatching { setForeground(buildForegroundInfo("Sync complete — nothing new to send", 0, 0)) }
            return Result.success()
        }

        var synced = 0
        var failed = 0
        var lastError: String? = null
        val newManifestEntries = mutableListOf<ManifestEntry>()

        try {
            when (profile.type) {
                SyncType.TELEGRAM -> {
                    val botToken = profile.telegramBotTokenKey?.let { encryptedPrefs.get(it) }
                    val chatId = profile.telegramChatId
                    if (botToken.isNullOrBlank() || chatId.isNullOrBlank()) {
                        throw IllegalStateException("Telegram credentials not configured")
                    }

                    for ((index, file) in files.withIndex()) {
                        runCatching {
                            setForeground(buildForegroundInfo("Syncing ${file.name}", index + 1, total))
                        }
                        setProgress(workDataOf(
                            KEY_PROGRESS_SYNCED to (index + 1),
                            KEY_PROGRESS_TOTAL to total
                        ))

                        val result = telegramSync.sendFile(botToken, chatId, file, profile.telegramCaptionTemplate)
                        if (result.success) {
                            fileRepository.markSynced(listOf(file.path), System.currentTimeMillis())
                            newManifestEntries.add(file.toManifestEntry(profile, SyncType.TELEGRAM))
                            synced++
                        } else {
                            failed++
                            lastError = result.error
                            Log.w(TAG, "Failed to sync ${file.name}: ${result.error}")
                        }
                        delay(500)
                    }
                }

                SyncType.EMAIL -> {
                    val password = profile.smtpPasswordKey?.let { encryptedPrefs.get(it) }
                    if (profile.smtpHost.isNullOrBlank() || profile.smtpUsername.isNullOrBlank() ||
                        password.isNullOrBlank() || profile.emailRecipient.isNullOrBlank()) {
                        throw IllegalStateException("Email credentials not configured")
                    }

                    val config = EmailSyncImpl.EmailConfig(
                        smtpHost = profile.smtpHost,
                        smtpPort = profile.smtpPort ?: 587,
                        username = profile.smtpUsername,
                        password = password,
                        recipient = profile.emailRecipient,
                        subjectTemplate = profile.emailSubjectTemplate ?: "[FileVault] Sync {date}"
                    )

                    runCatching {
                        setForeground(buildForegroundInfo("Sending ${files.size} files via email…", 0, total))
                    }

                    val result = emailSync.sendFiles(config, files)
                    synced = result.sentCount
                    failed = result.failedCount
                    lastError = result.error

                    if (result.success) {
                        val now = System.currentTimeMillis()
                        fileRepository.markSynced(files.map { it.path }, now)
                        newManifestEntries.addAll(files.map { it.toManifestEntry(profile, SyncType.EMAIL) })
                    }
                }
            }

            if (newManifestEntries.isNotEmpty()) {
                manifestManager.addEntries(newManifestEntries)
            }

            val finalStatus = when {
                failed == 0 -> SyncStatus.SUCCESS
                synced == 0 -> SyncStatus.FAILED
                else -> SyncStatus.PARTIAL
            }

            syncRepository.updateHistoryCompletion(historyId, System.currentTimeMillis(), synced, failed, finalStatus, lastError)
            syncRepository.updateLastSyncAt(profileId, System.currentTimeMillis())

            val summary = "✓ $synced synced${if (failed > 0) ", $failed failed" else ""}"
            runCatching { setForeground(buildForegroundInfo(summary, total, total)) }
            Log.d(TAG, "Sync complete: $synced synced, $failed failed")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            if (newManifestEntries.isNotEmpty()) manifestManager.addEntries(newManifestEntries)
            syncRepository.updateHistoryCompletion(
                historyId, System.currentTimeMillis(), synced, failed, SyncStatus.FAILED, e.message
            )
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun buildForegroundInfo(message: String, progress: Int, total: Int): ForegroundInfo {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("FileVault Sync")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (total > 0) setProgress(total, progress, false)
                else setProgress(0, 0, true)
            }
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "File Sync", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shows sync progress"
                    }
                )
            }
        }
    }
}

private fun FileEntry.toManifestEntry(profile: SyncProfile, syncType: SyncType) = ManifestEntry(
    path = path,
    name = name,
    sizeBytes = sizeBytes,
    syncedAt = System.currentTimeMillis(),
    profileName = profile.name,
    syncType = syncType.name
)
