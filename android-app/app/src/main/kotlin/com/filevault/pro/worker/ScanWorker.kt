package com.filevault.pro.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.filevault.pro.data.preferences.AppPreferences
import com.filevault.pro.domain.repository.FileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val fileRepository: FileRepository,
    private val appPreferences: AppPreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ScanWorker"
        const val KEY_IS_INITIAL = "is_initial_scan"
    }

    override suspend fun doWork(): Result {
        val isInitial = inputData.getBoolean(KEY_IS_INITIAL, false)
        Log.d(TAG, "ScanWorker starting (initial=$isInitial)")
        return try {
            var totalCount = 0

            val mediaCount = fileRepository.performMediaStoreScan()
            totalCount += mediaCount
            Log.d(TAG, "MediaStore scan: $mediaCount files")

            val fsCount = fileRepository.performFileSystemWalk { folder, count ->
                Log.v(TAG, "Walking: $folder ($count files so far)")
            }
            totalCount += fsCount
            Log.d(TAG, "File system walk: $fsCount files")

            appPreferences.setLastScanAt(System.currentTimeMillis())
            if (isInitial) {
                appPreferences.setInitialScanDone(true)
            }

            Log.d(TAG, "Scan complete. Total processed: $totalCount")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }
}
