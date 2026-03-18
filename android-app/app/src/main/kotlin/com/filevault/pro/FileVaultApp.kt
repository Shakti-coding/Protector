package com.filevault.pro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.filevault.pro.domain.repository.NotificationRepository
import com.filevault.pro.presentation.screen.notifications.NotificationStore
import com.filevault.pro.util.CrashLogStore
import com.filevault.pro.worker.ScanWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class FileVaultApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationRepository: NotificationRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        CrashLogStore.install(this)
        NotificationStore.repository = notificationRepository
        createNotificationChannels()
        scheduleScanWorker()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            listOf(
                NotificationChannel(
                    "scan_service_channel",
                    "File Scan Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background file scanning service notifications"
                    setShowBadge(false)
                },
                NotificationChannel(
                    "sync_channel",
                    "File Sync",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "File sync status notifications"
                },
                NotificationChannel(
                    "worker_channel",
                    "Background Workers",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background task notifications"
                    setShowBadge(false)
                }
            ).forEach { manager.createNotificationChannel(it) }
        }
    }

    private fun scheduleScanWorker() {
        val scanRequest = PeriodicWorkRequestBuilder<ScanWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .addTag("file_scan")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "periodic_file_scan",
            ExistingPeriodicWorkPolicy.KEEP,
            scanRequest
        )
    }

    private fun startRealtimeMonitoring() {
        try {
            val intent = android.content.Intent(this, com.filevault.pro.service.ScanForegroundService::class.java).apply {
                action = com.filevault.pro.service.ScanForegroundService.ACTION_START_MONITORING
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {}
    }
}
