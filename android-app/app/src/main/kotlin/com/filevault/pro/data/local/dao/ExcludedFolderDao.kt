package com.filevault.pro.data.local.dao

import androidx.room.*
import com.filevault.pro.data.local.entity.ExcludedFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcludedFolderDao {

    @Query("SELECT * FROM excluded_folders ORDER BY added_at DESC")
    fun getAll(): Flow<List<ExcludedFolderEntity>>

    @Query("SELECT folder_path FROM excluded_folders")
    suspend fun getAllPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ExcludedFolderEntity)

    @Query("DELETE FROM excluded_folders WHERE folder_path = :path")
    suspend fun deleteByPath(path: String)

    @Query("SELECT COUNT(*) FROM excluded_folders WHERE folder_path = :path")
    suspend fun isExcluded(path: String): Int
}
