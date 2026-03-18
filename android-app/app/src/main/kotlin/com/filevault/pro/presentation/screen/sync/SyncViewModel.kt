package com.filevault.pro.presentation.screen.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
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
        onDone(id)
    }

    fun syncNow(profileId: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_PROFILE_ID to profileId))
            .addTag("manual_sync_$profileId")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    suspend fun getProfileById(id: Long): SyncProfile? = syncRepository.getProfileById(id)
}
