package com.filevault.pro.domain.repository

import com.filevault.pro.domain.model.SyncHistory
import com.filevault.pro.domain.model.SyncProfile
import com.filevault.pro.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun getAllProfiles(): Flow<List<SyncProfile>>
    suspend fun getActiveProfiles(): List<SyncProfile>
    suspend fun getProfileById(id: Long): SyncProfile?
    suspend fun upsertProfile(profile: SyncProfile): Long
    suspend fun deleteProfile(id: Long)
    suspend fun setProfileActive(id: Long, active: Boolean)
    suspend fun updateLastSyncAt(id: Long, syncAt: Long)

    fun getAllHistory(): Flow<List<SyncHistory>>
    fun getHistoryForProfile(profileId: Long): Flow<List<SyncHistory>>
    suspend fun insertHistory(history: SyncHistory): Long
    suspend fun updateHistoryCompletion(id: Long, completedAt: Long, synced: Int, failed: Int, status: SyncStatus, error: String?)
    suspend fun deleteHistory(id: Long)
    suspend fun cleanOldHistory(olderThanMs: Long)
}
