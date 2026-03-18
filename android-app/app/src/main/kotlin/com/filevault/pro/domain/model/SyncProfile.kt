package com.filevault.pro.domain.model

data class SyncProfile(
    val id: Long = 0,
    val name: String,
    val type: SyncType,
    val isActive: Boolean = true,
    val intervalHours: Int = 24,
    val fileTypeScope: List<FileType> = emptyList(),
    val lastSyncAt: Long? = null,

    // Email
    val smtpHost: String? = null,
    val smtpPort: Int? = null,
    val smtpUsername: String? = null,
    val smtpPasswordKey: String? = null,
    val emailRecipient: String? = null,
    val emailSubjectTemplate: String? = "[FileVault] Sync {date} - {filecount} files",

    // Telegram
    val telegramBotTokenKey: String? = null,
    val telegramChatId: String? = null,
    val telegramCaptionTemplate: String? = "{filename} | {date}",

    val createdAt: Long = System.currentTimeMillis()
)

enum class SyncType {
    EMAIL, TELEGRAM
}

data class SyncHistory(
    val id: Long = 0,
    val profileId: Long,
    val profileName: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val filesSynced: Int = 0,
    val filesFailed: Int = 0,
    val status: SyncStatus = SyncStatus.IN_PROGRESS,
    val errorMessage: String? = null
)

enum class SyncStatus {
    IN_PROGRESS, SUCCESS, PARTIAL, FAILED
}

data class SortOrder(
    val field: SortField = SortField.DATE_MODIFIED,
    val ascending: Boolean = false
)

enum class SortField {
    DATE_MODIFIED, DATE_ADDED, NAME, SIZE, FOLDER, DATE_TAKEN, DURATION
}

data class FileFilter(
    val fileTypes: Set<FileType> = emptySet(),
    val folderPaths: Set<String> = emptySet(),
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val searchQuery: String = "",
    val showHidden: Boolean = false,
    val showDeleted: Boolean = false,
    val cameraMake: String? = null,
    val hasGpsOnly: Boolean = false,
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null
)
