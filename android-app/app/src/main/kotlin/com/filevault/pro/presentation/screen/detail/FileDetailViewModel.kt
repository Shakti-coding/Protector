package com.filevault.pro.presentation.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FileDetailViewModel @Inject constructor(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _file = MutableStateFlow<FileEntry?>(null)
    val file: StateFlow<FileEntry?> = _file.asStateFlow()

    fun loadFile(path: String) {
        viewModelScope.launch {
            fileRepository.getAllFiles(
                com.filevault.pro.domain.model.SortOrder(),
                com.filevault.pro.domain.model.FileFilter(searchQuery = path)
            ).collect { files ->
                _file.value = files.firstOrNull { it.path == path }
            }
        }
    }

    fun toggleSyncIgnore(path: String, ignored: Boolean) {
        viewModelScope.launch { fileRepository.setSyncIgnored(path, ignored) }
    }
}
