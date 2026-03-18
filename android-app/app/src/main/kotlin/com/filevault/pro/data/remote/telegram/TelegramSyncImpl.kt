package com.filevault.pro.data.remote.telegram

import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramSyncImpl @Inject constructor(private val api: TelegramApiService) {

    data class SyncResult(val success: Boolean, val error: String? = null)

    suspend fun validateBotToken(token: String): Pair<Boolean, String?> {
        return try {
            val response = api.getMe(token)
            if (response.isSuccessful && response.body()?.ok == true) {
                Pair(true, response.body()?.result?.username)
            } else {
                Pair(false, response.body()?.description ?: "Invalid token")
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Network error")
        }
    }

    suspend fun sendFile(
        botToken: String,
        chatId: String,
        fileEntry: FileEntry,
        captionTemplate: String?
    ): SyncResult {
        val file = File(fileEntry.path)
        if (!file.exists()) return SyncResult(false, "File not found on device")
        if (file.length() > 50 * 1024 * 1024) return SyncResult(false, "File exceeds 50 MB Telegram limit")

        val caption = buildCaption(captionTemplate, fileEntry)
        val chatIdBody = chatId.toRequestBody("text/plain".toMediaType())
        val captionBody = if (!caption.isNullOrBlank()) caption.toRequestBody("text/plain".toMediaType()) else null
        val mimeType = fileEntry.mimeType.ifBlank { "application/octet-stream" }
        val fileBody = file.asRequestBody(mimeType.toMediaType())

        return try {
            val response = when (fileEntry.fileType) {
                FileType.PHOTO -> {
                    val part = MultipartBody.Part.createFormData("photo", file.name, fileBody)
                    api.sendPhoto(botToken, chatIdBody, part, captionBody)
                }
                FileType.VIDEO -> {
                    val part = MultipartBody.Part.createFormData("video", file.name, fileBody)
                    api.sendVideo(botToken, chatIdBody, part, captionBody)
                }
                FileType.AUDIO -> {
                    val part = MultipartBody.Part.createFormData("audio", file.name, fileBody)
                    api.sendAudio(botToken, chatIdBody, part, captionBody)
                }
                else -> {
                    val part = MultipartBody.Part.createFormData("document", file.name, fileBody)
                    api.sendDocument(botToken, chatIdBody, part, captionBody)
                }
            }

            if (response.isSuccessful && response.body()?.ok == true) {
                SyncResult(true)
            } else {
                SyncResult(false, response.body()?.description ?: "Telegram API error ${response.code()}")
            }
        } catch (e: Exception) {
            SyncResult(false, e.message ?: "Network error")
        }
    }

    private fun buildCaption(template: String?, file: FileEntry): String? {
        if (template.isNullOrBlank()) return null
        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(file.lastModified))
        return template
            .replace("{filename}", file.name)
            .replace("{date}", date)
            .replace("{size}", com.filevault.pro.util.FileUtils.formatSize(file.sizeBytes))
            .replace("{folder}", file.folderName)
            .replace("{type}", file.fileType.name)
    }
}
