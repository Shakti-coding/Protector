package com.filevault.pro.presentation.screen.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileFilter
import com.filevault.pro.domain.model.FileType
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
class FilesViewModel @Inject constructor(private val fileRepository: FileRepository) : ViewModel() {

    private val _sortOrder = MutableStateFlow(SortOrder(SortField.DATE_MODIFIED, false))
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedTypes = MutableStateFlow(setOf<FileType>())
    val selectedTypes: StateFlow<Set<FileType>> = _selectedTypes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGroupByFolder = MutableStateFlow(false)
    val isGroupByFolder: StateFlow<Boolean> = _isGroupByFolder.asStateFlow()

    val files: StateFlow<List<FileEntry>> = combine(_sortOrder, _selectedTypes, _searchQuery) { sort, types, query ->
        Triple(sort, FileFilter(fileTypes = types, searchQuery = query), query)
    }
        .debounce(300)
        .flatMapLatest { (sort, filter, _) -> fileRepository.getAllFiles(sort, filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setSortOrder(o: SortOrder) { _sortOrder.value = o }
    fun toggleTypeFilter(type: FileType) {
        _selectedTypes.value = if (type in _selectedTypes.value) _selectedTypes.value - type else _selectedTypes.value + type
    }
    fun toggleGroupByFolder() { _isGroupByFolder.value = !_isGroupByFolder.value }
    fun setSyncIgnored(path: String, ignored: Boolean) {
        viewModelScope.launch { fileRepository.setSyncIgnored(path, ignored) }
    }
}
