package com.filevault.pro.data.local.dao

import androidx.room.*
import com.filevault.pro.data.local.entity.SyncProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncProfileDao {

    @Query("SELECT * FROM sync_profiles ORDER BY created_at DESC")
    fun getAllProfiles(): Flow<List<SyncProfileEntity>>

    @Query("SELECT * FROM sync_profiles WHERE is_active = 1 ORDER BY created_at DESC")
    suspend fun getActiveProfiles(): List<SyncProfileEntity>

    @Query("SELECT * FROM sync_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SyncProfileEntity?

    @Upsert
    suspend fun upsert(profile: SyncProfileEntity): Long

    @Delete
    suspend fun delete(profile: SyncProfileEntity)

    @Query("DELETE FROM sync_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sync_profiles SET last_sync_at = :syncAt WHERE id = :id")
    suspend fun updateLastSyncAt(id: Long, syncAt: Long)

    @Query("UPDATE sync_profiles SET is_active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}
