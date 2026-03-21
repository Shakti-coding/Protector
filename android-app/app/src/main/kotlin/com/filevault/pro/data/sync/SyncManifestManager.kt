package com.filevault.pro.data.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ManifestEntry(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val syncedAt: Long,
    val profileName: String,
    val syncType: String
)

@Singleton
class SyncManifestManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SyncManifestManager"
        private const val MANIFEST_FILE = "sync_manifest.json"
    }

    private val manifestFile = File(context.filesDir, MANIFEST_FILE)

    fun loadManifest(): List<ManifestEntry> {
        if (!manifestFile.exists()) return emptyList()
        return runCatching {
            val json = JSONObject(manifestFile.readText())
            val arr = json.getJSONArray("entries")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ManifestEntry(
                    path = obj.getString("path"),
                    name = obj.getString("name"),
                    sizeBytes = obj.getLong("sizeBytes"),
                    syncedAt = obj.getLong("syncedAt"),
                    profileName = obj.optString("profileName", ""),
                    syncType = obj.optString("syncType", "")
                )
            }
        }.getOrElse {
            Log.e(TAG, "Failed to load manifest", it)
            emptyList()
        }
    }

    fun getSyncedPaths(): Set<String> = loadManifest().map { it.path }.toSet()

    fun addEntries(entries: List<ManifestEntry>) {
        val current = loadManifest().associateBy { it.path }.toMutableMap()
        entries.forEach { current[it.path] = it }
        saveManifest(current.values.toList())
    }

    private fun saveManifest(entries: List<ManifestEntry>) {
        runCatching {
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(JSONObject().apply {
                    put("path", e.path)
                    put("name", e.name)
                    put("sizeBytes", e.sizeBytes)
                    put("syncedAt", e.syncedAt)
                    put("profileName", e.profileName)
                    put("syncType", e.syncType)
                })
            }
            val json = JSONObject().apply {
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                put("entryCount", entries.size)
                put("entries", arr)
            }
            manifestFile.writeText(json.toString(2))
        }.onFailure { Log.e(TAG, "Failed to save manifest", it) }
    }

    suspend fun exportManifest(destUri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val os = context.contentResolver.openOutputStream(destUri) ?: return@runCatching false
            os.bufferedWriter().use { it.write(manifestFile.readText()) }
            true
        }.getOrDefault(false)
    }

    suspend fun importManifest(srcUri: Uri): Int = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(srcUri)?.bufferedReader()?.readText()
                ?: return@runCatching 0
            val json = JSONObject(text)
            val arr = json.getJSONArray("entries")
            val imported = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ManifestEntry(
                    path = obj.getString("path"),
                    name = obj.getString("name"),
                    sizeBytes = obj.getLong("sizeBytes"),
                    syncedAt = obj.getLong("syncedAt"),
                    profileName = obj.optString("profileName", ""),
                    syncType = obj.optString("syncType", "")
                )
            }
            addEntries(imported)
            imported.size
        }.getOrDefault(0)
    }

    fun manifestSummary(): String {
        val entries = loadManifest()
        if (entries.isEmpty()) return "No sync manifest — 0 entries"
        val lastSync = entries.maxOfOrNull { it.syncedAt } ?: 0L
        val fmt = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return "${entries.size} files synced · Last: ${fmt.format(Date(lastSync))}"
    }

    fun clearManifest() {
        manifestFile.delete()
    }
}
