package com.filevault.pro.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.filevault.pro.data.preferences.EncryptedPrefs
import com.filevault.pro.data.remote.email.EmailSyncImpl
import com.filevault.pro.data.remote.telegram.TelegramSyncImpl
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
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val fileRepository: FileRepository,
    private val telegramSync: TelegramSyncImpl,
    private val emailSync: EmailSyncImpl,
    private val encryptedPrefs: EncryptedPrefs
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        const val KEY_PROFILE_ID = "profile_id"
    }

    override suspend fun doWork(): Result {
        val profileId = inputData.getLong(KEY_PROFILE_ID, -1)
        if (profileId == -1L) return Result.failure()

        val profile = syncRepository.getProfileById(profileId) ?: return Result.failure()
        if (!profile.isActive) return Result.success()

        Log.d(TAG, "Starting sync for profile: ${profile.name}")

        val historyId = syncRepository.insertHistory(
            SyncHistory(
                profileId = profile.id,
                profileName = profile.name,
                startedAt = System.currentTimeMillis(),
                status = SyncStatus.IN_PROGRESS
            )
        )

        val files = fileRepository.getUnsyncedFiles(profile.fileTypeScope)
        Log.d(TAG, "Files to sync: ${files.size}")

        var synced = 0
        var failed = 0
        var lastError: String? = null

        try {
            when (profile.type) {
                SyncType.TELEGRAM -> {
                    val botToken = profile.telegramBotTokenKey?.let { encryptedPrefs.get(it) }
                    val chatId = profile.telegramChatId
                    if (botToken.isNullOrBlank() || chatId.isNullOrBlank()) {
                        throw IllegalStateException("Telegram credentials not configured")
                    }

                    for (file in files) {
                        val result = telegramSync.sendFile(botToken, chatId, file, profile.telegramCaptionTemplate)
                        if (result.success) {
                            fileRepository.markSynced(listOf(file.path), System.currentTimeMillis())
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

                    val result = emailSync.sendFiles(config, files)
                    synced = result.sentCount
                    failed = result.failedCount
                    lastError = result.error

                    if (result.success) {
                        fileRepository.markSynced(files.map { it.path }, System.currentTimeMillis())
                    }
                }
            }

            val finalStatus = when {
                failed == 0 -> SyncStatus.SUCCESS
                synced == 0 -> SyncStatus.FAILED
                else -> SyncStatus.PARTIAL
            }

            syncRepository.updateHistoryCompletion(historyId, System.currentTimeMillis(), synced, failed, finalStatus, lastError)
            syncRepository.updateLastSyncAt(profileId, System.currentTimeMillis())

            Log.d(TAG, "Sync complete: $synced synced, $failed failed")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            syncRepository.updateHistoryCompletion(
                historyId, System.currentTimeMillis(), synced, failed, SyncStatus.FAILED, e.message
            )
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
