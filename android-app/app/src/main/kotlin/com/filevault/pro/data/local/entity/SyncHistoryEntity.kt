package com.filevault.pro.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.filevault.pro.domain.model.SyncHistory
import com.filevault.pro.domain.model.SyncStatus

@Entity(
    tableName = "sync_history",
    indices = [Index("profile_id"), Index("started_at")]
)
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val profileName: String,
    val startedAt: Long,
    val completedAt: Long?,
    val filesSynced: Int,
    val filesFailed: Int,
    val status: String,
    val errorMessage: String?
)

fun SyncHistoryEntity.toDomain() = SyncHistory(
    id = id,
    profileId = profileId,
    profileName = profileName,
    startedAt = startedAt,
    completedAt = completedAt,
    filesSynced = filesSynced,
    filesFailed = filesFailed,
    status = SyncStatus.valueOf(status),
    errorMessage = errorMessage
)

fun SyncHistory.toEntity() = SyncHistoryEntity(
    id = id,
    profileId = profileId,
    profileName = profileName,
    startedAt = startedAt,
    completedAt = completedAt,
    filesSynced = filesSynced,
    filesFailed = filesFailed,
    status = status.name,
    errorMessage = errorMessage
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val folderPath: String,
    val addedAt: Long = System.currentTimeMillis()
)
