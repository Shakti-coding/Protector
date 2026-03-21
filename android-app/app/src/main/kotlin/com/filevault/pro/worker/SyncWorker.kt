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

    override suspend fun doWork(): Result {
        val profileId = inputData.getLong(KEY_PROFILE_ID, -1L)

        // ── Outer safety net: catches ANY pre-history failure and records it ──
        if (profileId == -1L) {
            Log.e(TAG, "doWork called with no profile_id in input data")
            return Result.failure(workDataOf("error" to "No profile_id in work data"))
        }

        val profile = try {
            syncRepository.getProfileById(profileId)
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading profile $profileId: ${e.message}", e)
            // Write a failure history even if profile load crashes
            runCatching {
                syncRepository.insertHistory(
                    SyncHistory(
                        profileId = profileId,
                        profileName = "Profile #$profileId",
                        startedAt = System.currentTimeMillis(),
                        status = SyncStatus.FAILED,
                        completedAt = System.currentTimeMillis(),
                        errorMessage = "Failed to load profile: ${e.message}"
                    )
                )
            }
            return Result.failure()
        }

        if (profile == null) {
            Log.e(TAG, "Profile $profileId not found in database")
            runCatching {
                syncRepository.insertHistory(
                    SyncHistory(
                        profileId = profileId,
                        profileName = "Profile #$profileId",
                        startedAt = System.currentTimeMillis(),
                        status = SyncStatus.FAILED,
                        completedAt = System.currentTimeMillis(),
                        errorMessage = "Profile not found in database (id=$profileId)"
                    )
                )
            }
            return Result.failure()
        }

        val forceSync = inputData.getBoolean(KEY_FORCE_SYNC, false)
        if (!profile.isActive && !forceSync) {
            Log.d(TAG, "Profile ${profile.name} is inactive and forceSync=false — skipping")
            return Result.success()
        }

        // ── History entry created HERE — guaranteed to run before any sync logic ──
        val historyId = try {
            syncRepository.insertHistory(
                SyncHistory(
                    profileId = profile.id,
                    profileName = profile.name,
                    startedAt = System.currentTimeMillis(),
                    status = SyncStatus.IN_PROGRESS
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert history record: ${e.message}", e)
            return Result.failure()
        }

        Log.d(TAG, "Starting sync for profile: ${profile.name} (id=$profileId, historyId=$historyId)")
        runCatching { setForeground(buildForegroundInfo("Starting: ${profile.name}", 0, 0)) }
            .onFailure { Log.w(TAG, "setForeground (start) failed non-fatally: ${it.message}") }

        // ── File discovery: use ALL syncable files, manifest is the sole dedup guard ──
        val allFiles = try {
            fileRepository.getAllSyncableFiles(profile.fileTypeScope)
        } catch (e: Exception) {
            Log.e(TAG, "File query failed: ${e.message}", e)
            syncRepository.updateHistoryCompletion(
                historyId, System.currentTimeMillis(), 0, 0,
                SyncStatus.FAILED, "File query error: ${e.message}"
            )
            return Result.failure()
        }

        val alreadySynced: Set<String> = manifestManager.getSyncedPaths()
        val files = allFiles.filter { it.path !in alreadySynced }
        val total = files.size

        Log.d(TAG, "Files scope=${profile.fileTypeScope.map{it.name}}, total=${allFiles.size}, " +
                "already-in-manifest=${alreadySynced.size}, to-send=$total")

        if (total == 0) {
            val msg = if (allFiles.isEmpty()) "No files found for selected types"
                      else "All ${allFiles.size} file(s) already synced (manifest)"
            Log.d(TAG, msg)
            syncRepository.updateHistoryCompletion(
                historyId, System.currentTimeMillis(), 0, 0, SyncStatus.SUCCESS, null
            )
            syncRepository.updateLastSyncAt(profileId, System.currentTimeMillis())
            runCatching { setForeground(buildForegroundInfo("Nothing new to send", 0, 0)) }
            return Result.success()
        }

        var synced = 0
        var failed = 0
        var lastError: String? = null

        try {
            when (profile.type) {
                SyncType.TELEGRAM -> {
                    val botToken = profile.telegramBotTokenKey?.let { encryptedPrefs.get(it) }
                    val chatId = profile.telegramChatId
                    if (botToken.isNullOrBlank() || chatId.isNullOrBlank()) {
                        throw IllegalStateException(
                            "Telegram credentials missing — bot token or chat ID not set"
                        )
                    }

                    for ((index, file) in files.withIndex()) {
                        // ── Stop point: honour WorkManager cancellation ──
                        if (isStopped) {
                            Log.d(TAG, "Worker stopped by system at file $index/$total")
                            break
                        }

                        runCatching {
                            setForeground(buildForegroundInfo(
                                "Sending ${index + 1}/$total: ${file.name}", index + 1, total
                            ))
                        }
                        setProgress(workDataOf(
                            KEY_PROGRESS_SYNCED to (index + 1),
                            KEY_PROGRESS_TOTAL to total
                        ))

                        val result = telegramSync.sendFile(
                            botToken, chatId, file, profile.telegramCaptionTemplate
                        )

                        if (result.success) {
                            // ── Write manifest immediately — safe against mid-run crashes ──
                            runCatching {
                                manifestManager.addEntries(
                                    listOf(file.toManifestEntry(profile, SyncType.TELEGRAM))
                                )
                            }
                            fileRepository.markSynced(listOf(file.path), System.currentTimeMillis())
                            synced++
                            Log.d(TAG, "✓ Sent ${file.name} ($synced/$total)")
                        } else {
                            failed++
                            lastError = result.error
                            Log.w(TAG, "✗ Failed ${file.name}: ${result.error}")
                        }

                        // Telegram rate-limit guard
                        delay(600)
                    }
                }

                SyncType.EMAIL -> {
                    val password = profile.smtpPasswordKey?.let { encryptedPrefs.get(it) }
                    if (profile.smtpHost.isNullOrBlank() || profile.smtpUsername.isNullOrBlank() ||
                        password.isNullOrBlank() || profile.emailRecipient.isNullOrBlank()) {
                        throw IllegalStateException(
                            "Email credentials missing — check SMTP host, username, password and recipient"
                        )
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
                        runCatching {
                            manifestManager.addEntries(
                                files.map { it.toManifestEntry(profile, SyncType.EMAIL) }
                            )
                        }
                    }
                }
            }

            val finalStatus = when {
                failed == 0 && synced > 0 -> SyncStatus.SUCCESS
                synced == 0 && failed > 0  -> SyncStatus.FAILED
                synced > 0 && failed > 0   -> SyncStatus.PARTIAL
                else                       -> SyncStatus.SUCCESS
            }

            syncRepository.updateHistoryCompletion(
                historyId, System.currentTimeMillis(), synced, failed, finalStatus, lastError
            )
            syncRepository.updateLastSyncAt(profileId, System.currentTimeMillis())

            val summary = "Done: $synced sent${if (failed > 0) ", $failed failed" else ""}"
            runCatching { setForeground(buildForegroundInfo(summary, total, total)) }
            Log.d(TAG, "Sync complete — $synced sent, $failed failed")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception: ${e.message}", e)
            val errMsg = e.message ?: e.javaClass.simpleName
            syncRepository.updateHistoryCompletion(
                historyId, System.currentTimeMillis(), synced, failed, SyncStatus.FAILED, errMsg
            )
            return if (runAttemptCount < 2) Result.retry() else Result.failure()
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
