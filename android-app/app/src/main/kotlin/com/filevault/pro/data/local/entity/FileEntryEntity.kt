package com.filevault.pro.data.local.entity

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
    val folderPath: String,
    val folderName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String,
    val fileType: String,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val orientation: Int?,
    val cameraMake: String?,
    val cameraModel: String?,
    val hasGps: Boolean,
    val dateTaken: Long?,
    val dateAdded: Long,
    val isHidden: Boolean,
    val contentHash: String?,
    val thumbnailCachePath: String?,
    val isSyncIgnored: Boolean,
    val lastSyncedAt: Long?,
    val isDeletedFromDevice: Boolean
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
