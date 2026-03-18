package com.filevault.pro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.filevault.pro.data.local.dao.ExcludedFolderDao
import com.filevault.pro.data.local.dao.FileEntryDao
import com.filevault.pro.data.local.dao.SyncHistoryDao
import com.filevault.pro.data.local.dao.SyncProfileDao
import com.filevault.pro.data.local.entity.ExcludedFolderEntity
import com.filevault.pro.data.local.entity.FileEntryEntity
import com.filevault.pro.data.local.entity.SyncHistoryEntity
import com.filevault.pro.data.local.entity.SyncProfileEntity

@Database(
    entities = [
        FileEntryEntity::class,
        SyncProfileEntity::class,
        SyncHistoryEntity::class,
        ExcludedFolderEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileEntryDao(): FileEntryDao
    abstract fun syncProfileDao(): SyncProfileDao
    abstract fun syncHistoryDao(): SyncHistoryDao
    abstract fun excludedFolderDao(): ExcludedFolderDao

    companion object {
        const val DATABASE_NAME = "filevault_db"
    }
}
