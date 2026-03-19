package com.filevault.pro.presentation.screen.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filevault.pro.data.preferences.AppPreferences
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileFilter
import com.filevault.pro.domain.model.SortField
import com.filevault.pro.domain.model.SortOrder
import com.filevault.pro.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(SortOrder(SortField.DATE_MODIFIED, false))
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _filter = MutableStateFlow(FileFilter())
    val filter: StateFlow<FileFilter> = _filter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    val gridColumns: StateFlow<Int> = appPreferences.gridColumnsPhotos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    @OptIn(ExperimentalCoroutinesApi::class)
    val photos: StateFlow<List<FileEntry>> = combine(_sortOrder, _filter, _searchQuery) { sort, filter, query ->
        filter.copy(searchQuery = query)
        Triple(sort, filter.copy(searchQuery = query), query)
    }
        .debounce(300)
        .flatMapLatest { (sort, filter, _) ->
            fileRepository.getAllPhotos(sort, filter)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun setFilter(filter: FileFilter) { _filter.value = filter }
    fun toggleView() { _isGridView.value = !_isGridView.value }
    fun setGridColumns(count: Int) {
        viewModelScope.launch { appPreferences.setGridColumnsPhotos(count) }
    }

    fun toggleSyncIgnore(path: String, ignored: Boolean) {
        viewModelScope.launch { fileRepository.setSyncIgnored(path, ignored) }
    }

    fun markDeletedFromApp(paths: Set<String>) {
        viewModelScope.launch {
            paths.forEach { fileRepository.markDeleted(it) }
        }
    }
}
