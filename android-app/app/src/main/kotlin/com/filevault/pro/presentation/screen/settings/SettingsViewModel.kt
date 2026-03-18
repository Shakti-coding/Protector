package com.filevault.pro.presentation.screen.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filevault.pro.data.preferences.AppPreferences
import com.filevault.pro.domain.model.FileFilter
import com.filevault.pro.domain.model.SortOrder
import com.filevault.pro.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val fileRepository: FileRepository
) : ViewModel() {

    val themeMode: StateFlow<String> = appPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val showHiddenFiles: StateFlow<Boolean> = appPreferences.showHiddenFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLockEnabled: StateFlow<Boolean> = appPreferences.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scanIntervalMinutes: StateFlow<Int> = appPreferences.scanIntervalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

    fun setShowHiddenFiles(show: Boolean) = viewModelScope.launch {
        appPreferences.setShowHiddenFiles(show)
    }

    fun setAppLockEnabled(enabled: Boolean) = viewModelScope.launch {
        appPreferences.setAppLockEnabled(enabled)
    }

    fun setScanIntervalMinutes(minutes: Int) = viewModelScope.launch {
        appPreferences.setScanIntervalMinutes(minutes)
    }

    fun cycleTheme() = viewModelScope.launch {
        val current = appPreferences.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM").value
        val next = when (current) {
            "SYSTEM" -> "LIGHT"
            "LIGHT" -> "DARK"
            else -> "SYSTEM"
        }
        appPreferences.setThemeMode(next)
    }

    fun exportCatalog(context: Context) {
        viewModelScope.launch {
            try {
                val files = fileRepository.getAllFiles(SortOrder(), FileFilter()).stateIn(
                    viewModelScope, SharingStarted.Eagerly, emptyList()
                ).value

                val sb = StringBuilder()
                sb.appendLine("path,name,folder,size_bytes,last_modified,mime_type,file_type,width,height,duration_ms,date_added")
                files.forEach { f ->
                    sb.appendLine("\"${f.path}\",\"${f.name}\",\"${f.folderName}\",${f.sizeBytes},${f.lastModified},\"${f.mimeType}\",${f.fileType.name},${f.width ?: ""},${f.height ?: ""},${f.durationMs ?: ""},${f.dateAdded}")
                }

                val exportFile = File(context.getExternalFilesDir(null), "filevault_catalog_${System.currentTimeMillis()}.csv")
                exportFile.writeText(sb.toString())

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", exportFile
                    ))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Export Catalog"))
            } catch (_: Exception) {}
        }
    }
}
