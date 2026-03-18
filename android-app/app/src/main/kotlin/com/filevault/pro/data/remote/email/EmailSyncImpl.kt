package com.filevault.pro.data.remote.email

import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.util.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

@Singleton
class EmailSyncImpl @Inject constructor() {

    data class EmailConfig(
        val smtpHost: String,
        val smtpPort: Int,
        val username: String,
        val password: String,
        val recipient: String,
        val subjectTemplate: String,
        val useSsl: Boolean = smtpPort == 465
    )

    data class SyncResult(
        val success: Boolean,
        val sentCount: Int = 0,
        val failedCount: Int = 0,
        val error: String? = null
    )

    suspend fun testConnection(config: EmailConfig): Pair<Boolean, String?> {
        return try {
            val session = createSession(config)
            val transport = session.getTransport("smtp")
            transport.connect(config.smtpHost, config.smtpPort, config.username, config.password)
            transport.close()
            Pair(true, null)
        } catch (e: MessagingException) {
            Pair(false, e.message ?: "Connection failed")
        } catch (e: Exception) {
            Pair(false, e.message ?: "Unknown error")
        }
    }

    suspend fun sendFiles(config: EmailConfig, files: List<FileEntry>): SyncResult {
        if (files.isEmpty()) return SyncResult(true, 0, 0)

        val maxBatchSize = 10 * 1024 * 1024L
        val batches = mutableListOf<List<FileEntry>>()
        val currentBatch = mutableListOf<FileEntry>()
        var batchSize = 0L

        for (file in files) {
            val fileSize = File(file.path).length()
            if (batchSize + fileSize > maxBatchSize && currentBatch.isNotEmpty()) {
                batches.add(currentBatch.toList())
                currentBatch.clear()
                batchSize = 0L
            }
            currentBatch.add(file)
            batchSize += fileSize
        }
        if (currentBatch.isNotEmpty()) batches.add(currentBatch.toList())

        var totalSent = 0
        var totalFailed = 0
        val errors = mutableListOf<String>()

        for ((index, batch) in batches.withIndex()) {
            try {
                val session = createSession(config)
                val message = MimeMessage(session)
                message.setFrom(InternetAddress(config.username))
                message.setRecipient(Message.RecipientType.TO, InternetAddress(config.recipient))

                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val subject = config.subjectTemplate
                    .replace("{date}", dateStr)
                    .replace("{filecount}", batch.size.toString())
                    .replace("{batch}", "${index + 1}/${batches.size}")

                message.subject = subject

                val multipart = MimeMultipart()

                val bodyPart = MimeBodyPart()
                val bodyText = buildBodyText(batch, index + 1, batches.size)
                bodyPart.setText(bodyText, "UTF-8")
                multipart.addBodyPart(bodyPart)

                for (fileEntry in batch) {
                    val file = File(fileEntry.path)
                    if (!file.exists()) {
                        totalFailed++
                        continue
                    }
                    val attachPart = MimeBodyPart()
                    attachPart.attachFile(file)
                    multipart.addBodyPart(attachPart)
                }

                message.setContent(multipart)

                Transport.send(message)
                totalSent += batch.size
            } catch (e: Exception) {
                totalFailed += batch.size
                errors.add("Batch ${index + 1}: ${e.message}")
            }
        }

        return SyncResult(
            success = totalFailed == 0,
            sentCount = totalSent,
            failedCount = totalFailed,
            error = errors.joinToString("; ").takeIf { it.isNotEmpty() }
        )
    }

    private fun buildBodyText(files: List<FileEntry>, batchNum: Int, totalBatches: Int): String {
        val sb = StringBuilder()
        sb.appendLine("FileVault Pro — Sync Batch $batchNum of $totalBatches")
        sb.appendLine("Synced: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("Files: ${files.size}")
        sb.appendLine()
        sb.appendLine("File List:")
        sb.appendLine("─────────────────────────────────")
        files.forEach { f ->
            sb.appendLine("• ${f.name}")
            sb.appendLine("  Path: ${f.path}")
            sb.appendLine("  Size: ${FileUtils.formatSize(f.sizeBytes)}")
            sb.appendLine("  Modified: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(f.lastModified))}")
            sb.appendLine("  Type: ${f.fileType.name}")
            sb.appendLine()
        }
        sb.appendLine("─────────────────────────────────")
        sb.appendLine("Sent by FileVault Pro")
        return sb.toString()
    }

    private fun createSession(config: EmailConfig): Session {
        val props = Properties().apply {
            if (config.useSsl) {
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            } else {
                put("mail.smtp.starttls.enable", "true")
            }
            put("mail.smtp.host", config.smtpHost)
            put("mail.smtp.port", config.smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.timeout", "30000")
            put("mail.smtp.connectiontimeout", "30000")
        }
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(config.username, config.password)
        })
    }
}
