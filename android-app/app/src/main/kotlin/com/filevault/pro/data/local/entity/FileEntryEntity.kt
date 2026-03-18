package com.filevault.pro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileType

@Entity(
    tableName = "file_entries",
    indices = [
        Index("folder_path"),
        Index("last_modified"),
        Index("file_type"),
        Index("date_added"),
        Index("mime_type"),
        Index("content_hash"),
        Index("is_deleted_from_device"),
        Index("last_synced_at"),
        Index("date_taken")
    ]
)
data class FileEntryEntity(
    @PrimaryKey val path: String,
    val name: String,
    @ColumnInfo(name = "folder_path") val folderPath: String,
    @ColumnInfo(name = "folder_name") val folderName: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "file_type") val fileType: String,
    val width: Int?,
    val height: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    val orientation: Int?,
    @ColumnInfo(name = "camera_make") val cameraMake: String?,
    @ColumnInfo(name = "camera_model") val cameraModel: String?,
    @ColumnInfo(name = "has_gps") val hasGps: Boolean,
    @ColumnInfo(name = "date_taken") val dateTaken: Long?,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean,
    @ColumnInfo(name = "content_hash") val contentHash: String?,
    @ColumnInfo(name = "thumbnail_cache_path") val thumbnailCachePath: String?,
    @ColumnInfo(name = "is_sync_ignored") val isSyncIgnored: Boolean,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long?,
    @ColumnInfo(name = "is_deleted_from_device") val isDeletedFromDevice: Boolean
)

fun FileEntryEntity.toDomain() = FileEntry(
    path = path,
    name = name,
    folderPath = folderPath,
    folderName = folderName,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    mimeType = mimeType,
    fileType = FileType.valueOf(fileType),
    width = width,
    height = height,
    durationMs = durationMs,
    orientation = orientation,
    cameraMake = cameraMake,
    cameraModel = cameraModel,
    hasGps = hasGps,
    dateTaken = dateTaken,
    dateAdded = dateAdded,
    isHidden = isHidden,
    contentHash = contentHash,
    thumbnailCachePath = thumbnailCachePath,
    isSyncIgnored = isSyncIgnored,
    lastSyncedAt = lastSyncedAt,
    isDeletedFromDevice = isDeletedFromDevice
)

fun FileEntry.toEntity() = FileEntryEntity(
    path = path,
    name = name,
    folderPath = folderPath,
    folderName = folderName,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    mimeType = mimeType,
    fileType = fileType.name,
    width = width,
    height = height,
    durationMs = durationMs,
    orientation = orientation,
    cameraMake = cameraMake,
    cameraModel = cameraModel,
    hasGps = hasGps,
    dateTaken = dateTaken,
    dateAdded = dateAdded,
    isHidden = isHidden,
    contentHash = contentHash,
    thumbnailCachePath = thumbnailCachePath,
    isSyncIgnored = isSyncIgnored,
    lastSyncedAt = lastSyncedAt,
    isDeletedFromDevice = isDeletedFromDevice
)
