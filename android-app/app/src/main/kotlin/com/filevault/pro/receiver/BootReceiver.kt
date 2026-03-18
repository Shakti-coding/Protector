package com.filevault.pro.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.filevault.pro.worker.ScanWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d("BootReceiver", "Device booted — rescheduling workers")
                scheduleScanWorker(context)
            }
        }
    }

    private fun scheduleScanWorker(context: Context) {
        val request = PeriodicWorkRequestBuilder<ScanWorker>(
            15, TimeUnit.MINUTES, 5, TimeUnit.MINUTES
        ).addTag("file_scan").build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "periodic_file_scan",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
