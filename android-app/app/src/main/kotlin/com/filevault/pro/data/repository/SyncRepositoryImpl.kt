package com.filevault.pro.data.repository

import com.filevault.pro.data.local.dao.SyncHistoryDao
import com.filevault.pro.data.local.dao.SyncProfileDao
import com.filevault.pro.data.local.entity.SyncHistoryEntity
import com.filevault.pro.data.local.entity.toDomain
import com.filevault.pro.data.local.entity.toEntity
import com.filevault.pro.domain.model.SyncHistory
import com.filevault.pro.domain.model.SyncProfile
import com.filevault.pro.domain.model.SyncStatus
import com.filevault.pro.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val profileDao: SyncProfileDao,
    private val historyDao: SyncHistoryDao
) : SyncRepository {

    override fun getAllProfiles(): Flow<List<SyncProfile>> =
        profileDao.getAllProfiles().map { it.map { e -> e.toDomain() } }

    override suspend fun getActiveProfiles(): List<SyncProfile> =
        profileDao.getActiveProfiles().map { it.toDomain() }

    override suspend fun getProfileById(id: Long): SyncProfile? =
        profileDao.getById(id)?.toDomain()

    override suspend fun upsertProfile(profile: SyncProfile): Long =
        profileDao.upsert(profile.toEntity())

    override suspend fun deleteProfile(id: Long) = profileDao.deleteById(id)

    override suspend fun setProfileActive(id: Long, active: Boolean) =
        profileDao.setActive(id, active)

    override suspend fun updateLastSyncAt(id: Long, syncAt: Long) =
        profileDao.updateLastSyncAt(id, syncAt)

    override fun getAllHistory(): Flow<List<SyncHistory>> =
        historyDao.getAllHistory().map { it.map { e -> e.toDomain() } }

    override fun getHistoryForProfile(profileId: Long): Flow<List<SyncHistory>> =
        historyDao.getHistoryForProfile(profileId).map { it.map { e -> e.toDomain() } }

    override suspend fun insertHistory(history: SyncHistory): Long =
        historyDao.insert(history.toEntity())

    override suspend fun updateHistoryCompletion(
        id: Long, completedAt: Long, synced: Int, failed: Int, status: SyncStatus, error: String?
    ) = historyDao.updateCompletion(id, completedAt, synced, failed, status.name, error)

    override suspend fun deleteHistory(id: Long) = historyDao.deleteById(id)

    override suspend fun cleanOldHistory(olderThanMs: Long) =
        historyDao.deleteOlderThan(olderThanMs)
}
