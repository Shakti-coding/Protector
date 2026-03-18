package com.filevault.pro.data.local.entity

import androidx.room.ColumnInfo
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
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "profile_name") val profileName: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "files_synced") val filesSynced: Int,
    @ColumnInfo(name = "files_failed") val filesFailed: Int,
    val status: String,
    @ColumnInfo(name = "error_message") val errorMessage: String?
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
