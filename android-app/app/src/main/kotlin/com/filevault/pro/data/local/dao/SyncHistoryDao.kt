package com.filevault.pro.data.local.dao

import androidx.room.*
import com.filevault.pro.data.local.entity.SyncHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncHistoryDao {

    @Query("SELECT * FROM sync_history ORDER BY started_at DESC")
    fun getAllHistory(): Flow<List<SyncHistoryEntity>>

    @Query("SELECT * FROM sync_history WHERE profile_id = :profileId ORDER BY started_at DESC")
    fun getHistoryForProfile(profileId: Long): Flow<List<SyncHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: SyncHistoryEntity): Long

    @Query("UPDATE sync_history SET completed_at = :completedAt, files_synced = :synced, files_failed = :failed, status = :status, error_message = :error WHERE id = :id")
    suspend fun updateCompletion(id: Long, completedAt: Long, synced: Int, failed: Int, status: String, error: String?)

    @Query("DELETE FROM sync_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_history WHERE started_at < :before")
    suspend fun deleteOlderThan(before: Long)
}
