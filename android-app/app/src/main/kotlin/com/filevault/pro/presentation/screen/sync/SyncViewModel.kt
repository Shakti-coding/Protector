package com.filevault.pro.presentation.screen.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.filevault.pro.domain.model.SyncHistory
import com.filevault.pro.domain.model.SyncProfile
import com.filevault.pro.domain.model.SyncType
import com.filevault.pro.domain.repository.SyncRepository
import com.filevault.pro.worker.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncRepository: SyncRepository
) : ViewModel() {

    val profiles: StateFlow<List<SyncProfile>> = syncRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHistory: StateFlow<List<SyncHistory>> = syncRepository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getHistoryForProfile(profileId: Long): StateFlow<List<SyncHistory>> =
        syncRepository.getHistoryForProfile(profileId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteProfile(id: Long) = viewModelScope.launch { syncRepository.deleteProfile(id) }

    fun setProfileActive(id: Long, active: Boolean) = viewModelScope.launch {
        syncRepository.setProfileActive(id, active)
    }

    fun saveProfile(profile: SyncProfile, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = syncRepository.upsertProfile(profile)
        val savedProfile = profile.copy(id = id)
        if (savedProfile.isActive && savedProfile.intervalHours > 0) {
            scheduleSyncWorker(savedProfile)
        }
        onDone(id)
    }

    fun syncNow(profileId: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_PROFILE_ID to profileId))
            .addTag("manual_sync_$profileId")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancelSyncWorker(profileId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("sync_profile_$profileId")
    }

    private fun scheduleSyncWorker(profile: SyncProfile) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            profile.intervalHours.toLong(), TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(workDataOf(SyncWorker.KEY_PROFILE_ID to profile.id))
            .addTag("sync_profile_${profile.id}")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_profile_${profile.id}",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    suspend fun getProfileById(id: Long): SyncProfile? = syncRepository.getProfileById(id)
}
