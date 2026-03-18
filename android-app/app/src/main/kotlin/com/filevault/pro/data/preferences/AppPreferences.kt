package com.filevault.pro.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        val LAST_SCAN_AT = longPreferencesKey("last_scan_at")
        val INITIAL_SCAN_DONE = booleanPreferencesKey("initial_scan_done")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GRID_COLUMNS_PHOTOS = intPreferencesKey("grid_columns_photos")
        val GRID_COLUMNS_VIDEOS = intPreferencesKey("grid_columns_videos")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_TYPE = stringPreferencesKey("app_lock_type")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val LAST_ACTIVE_TIME = longPreferencesKey("last_active_time")
        val LOCK_TIMEOUT_MINUTES = intPreferencesKey("lock_timeout_minutes")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val BATTERY_OPTIMIZATION_ASKED = booleanPreferencesKey("battery_opt_asked")
        val SCAN_INTERVAL_MINUTES = intPreferencesKey("scan_interval_minutes")
        val SCAN_HIDDEN_FOLDERS = booleanPreferencesKey("scan_hidden_folders")
        val EXPORT_INCLUDE_HASH = booleanPreferencesKey("export_include_hash")
    }

    val lastScanAt: Flow<Long?> = context.dataStore.data.map { it[LAST_SCAN_AT] }
    val initialScanDone: Flow<Boolean> = context.dataStore.data.map { it[INITIAL_SCAN_DONE] ?: false }
    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "SYSTEM" }
    val gridColumnsPhotos: Flow<Int> = context.dataStore.data.map { it[GRID_COLUMNS_PHOTOS] ?: 3 }
    val gridColumnsVideos: Flow<Int> = context.dataStore.data.map { it[GRID_COLUMNS_VIDEOS] ?: 2 }
    val showHiddenFiles: Flow<Boolean> = context.dataStore.data.map { it[SHOW_HIDDEN_FILES] ?: false }
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[APP_LOCK_ENABLED] ?: false }
    val appLockType: Flow<String> = context.dataStore.data.map { it[APP_LOCK_TYPE] ?: "NONE" }
    val pinHash: Flow<String?> = context.dataStore.data.map { it[PIN_HASH] }
    val lastActiveTime: Flow<Long?> = context.dataStore.data.map { it[LAST_ACTIVE_TIME] }
    val lockTimeoutMinutes: Flow<Int> = context.dataStore.data.map { it[LOCK_TIMEOUT_MINUTES] ?: 5 }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_DONE] ?: false }
    val scanIntervalMinutes: Flow<Int> = context.dataStore.data.map { it[SCAN_INTERVAL_MINUTES] ?: 15 }
    val scanHiddenFolders: Flow<Boolean> = context.dataStore.data.map { it[SCAN_HIDDEN_FOLDERS] ?: true }

    suspend fun setLastScanAt(time: Long) = context.dataStore.edit { it[LAST_SCAN_AT] = time }
    suspend fun setInitialScanDone(done: Boolean) = context.dataStore.edit { it[INITIAL_SCAN_DONE] = done }
    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[THEME_MODE] = mode }
    suspend fun setGridColumnsPhotos(count: Int) = context.dataStore.edit { it[GRID_COLUMNS_PHOTOS] = count }
    suspend fun setGridColumnsVideos(count: Int) = context.dataStore.edit { it[GRID_COLUMNS_VIDEOS] = count }
    suspend fun setShowHiddenFiles(show: Boolean) = context.dataStore.edit { it[SHOW_HIDDEN_FILES] = show }
    suspend fun setAppLockEnabled(enabled: Boolean) = context.dataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    suspend fun setAppLockType(type: String) = context.dataStore.edit { it[APP_LOCK_TYPE] = type }
    suspend fun setPinHash(hash: String) = context.dataStore.edit { it[PIN_HASH] = hash }
    suspend fun setLastActiveTime(time: Long) = context.dataStore.edit { it[LAST_ACTIVE_TIME] = time }
    suspend fun setLockTimeoutMinutes(minutes: Int) = context.dataStore.edit { it[LOCK_TIMEOUT_MINUTES] = minutes }
    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[ONBOARDING_DONE] = done }
    suspend fun setScanIntervalMinutes(minutes: Int) = context.dataStore.edit { it[SCAN_INTERVAL_MINUTES] = minutes }
    suspend fun setScanHiddenFolders(scan: Boolean) = context.dataStore.edit { it[SCAN_HIDDEN_FOLDERS] = scan }
    suspend fun setBatteryOptimizationAsked(asked: Boolean) = context.dataStore.edit { it[BATTERY_OPTIMIZATION_ASKED] = asked }
}
