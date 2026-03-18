package com.filevault.pro.domain.model

data class FileEntry(
    val path: String,
    val name: String,
    val folderPath: String,
    val folderName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String,
    val fileType: FileType,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val orientation: Int? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val hasGps: Boolean = false,
    val dateTaken: Long? = null,
    val dateAdded: Long,
    val isHidden: Boolean = false,
    val contentHash: String? = null,
    val thumbnailCachePath: String? = null,
    val isSyncIgnored: Boolean = false,
    val lastSyncedAt: Long? = null,
    val isDeletedFromDevice: Boolean = false
)

enum class FileType {
    PHOTO, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, OTHER;

    companion object {
        fun fromMimeType(mimeType: String): FileType {
            return when {
                mimeType.startsWith("image/") -> PHOTO
                mimeType.startsWith("video/") -> VIDEO
                mimeType.startsWith("audio/") -> AUDIO
                mimeType in documentMimes -> DOCUMENT
                mimeType in archiveMimes -> ARCHIVE
                mimeType == "application/vnd.android.package-archive" -> APK
                else -> OTHER
            }
        }

        fun fromExtension(ext: String): FileType {
            return when (ext.lowercase()) {
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif",
                "tiff", "tif", "raw", "cr2", "nef", "orf", "arw" -> PHOTO
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm",
                "ts", "m4v", "mpg", "mpeg", "divx" -> VIDEO
                "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus",
                "aiff", "alac" -> AUDIO
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt",
                "rtf", "odt", "ods", "odp", "csv", "html", "xml", "json",
                "md", "epub", "fb2" -> DOCUMENT
                "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "lz4" -> ARCHIVE
                "apk", "xapk", "apks" -> APK
                else -> OTHER
            }
        }

        private val documentMimes = setOf(
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/html", "text/csv", "application/json",
            "application/xml", "text/xml"
        )

        private val archiveMimes = setOf(
            "application/zip", "application/x-rar-compressed",
            "application/x-7z-compressed", "application/x-tar",
            "application/gzip", "application/x-bzip2"
        )
    }
}

data class FolderInfo(
    val path: String,
    val name: String,
    val fileCount: Int,
    val totalSizeBytes: Long,
    val lastModified: Long,
    val thumbnailPath: String? = null
)

data class DuplicateGroup(
    val contentHash: String,
    val sizeBytes: Long,
    val files: List<FileEntry>
)

data class CatalogStats(
    val totalFiles: Int,
    val totalPhotos: Int,
    val totalVideos: Int,
    val totalAudio: Int,
    val totalDocuments: Int,
    val totalOther: Int,
    val totalSizeBytes: Long,
    val lastScanAt: Long?,
    val lastSyncAt: Long?
)
