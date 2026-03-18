package com.filevault.pro.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.filevault.pro.domain.model.FileType
import com.filevault.pro.domain.model.SyncProfile
import com.filevault.pro.domain.model.SyncType

@Entity(tableName = "sync_profiles")
data class SyncProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val isActive: Boolean,
    val intervalHours: Int,
    val fileTypeScope: String,
    val lastSyncAt: Long?,
    val smtpHost: String?,
    val smtpPort: Int?,
    val smtpUsername: String?,
    val smtpPasswordKey: String?,
    val emailRecipient: String?,
    val emailSubjectTemplate: String?,
    val telegramBotTokenKey: String?,
    val telegramChatId: String?,
    val telegramCaptionTemplate: String?,
    val createdAt: Long
)

fun SyncProfileEntity.toDomain() = SyncProfile(
    id = id,
    name = name,
    type = SyncType.valueOf(type),
    isActive = isActive,
    intervalHours = intervalHours,
    fileTypeScope = if (fileTypeScope == "ALL") FileType.values().toList()
                   else fileTypeScope.split(",").filter { it.isNotBlank() }.map { FileType.valueOf(it) },
    lastSyncAt = lastSyncAt,
    smtpHost = smtpHost,
    smtpPort = smtpPort,
    smtpUsername = smtpUsername,
    smtpPasswordKey = smtpPasswordKey,
    emailRecipient = emailRecipient,
    emailSubjectTemplate = emailSubjectTemplate,
    telegramBotTokenKey = telegramBotTokenKey,
    telegramChatId = telegramChatId,
    telegramCaptionTemplate = telegramCaptionTemplate,
    createdAt = createdAt
)

fun SyncProfile.toEntity() = SyncProfileEntity(
    id = id,
    name = name,
    type = type.name,
    isActive = isActive,
    intervalHours = intervalHours,
    fileTypeScope = if (fileTypeScope.isEmpty() || fileTypeScope.size == FileType.values().size)
                       "ALL" else fileTypeScope.joinToString(",") { it.name },
    lastSyncAt = lastSyncAt,
    smtpHost = smtpHost,
    smtpPort = smtpPort,
    smtpUsername = smtpUsername,
    smtpPasswordKey = smtpPasswordKey,
    emailRecipient = emailRecipient,
    emailSubjectTemplate = emailSubjectTemplate,
    telegramBotTokenKey = telegramBotTokenKey,
    telegramChatId = telegramChatId,
    telegramCaptionTemplate = telegramCaptionTemplate,
    createdAt = createdAt
)
