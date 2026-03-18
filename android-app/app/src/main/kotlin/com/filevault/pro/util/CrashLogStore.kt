package com.filevault.pro.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CrashEntry(
    val id: String,
    val timestamp: Long,
    val threadName: String,
    val message: String,
    val stackTrace: String
) {
    val formattedDate: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    val fullLog: String
        get() = buildString {
            appendLine("=== FileVault Pro Crash Report ===")
            appendLine("Date     : $formattedDate")
            appendLine("Thread   : $threadName")
            appendLine("Message  : $message")
            appendLine()
            appendLine("--- Stack Trace ---")
            appendLine(stackTrace)
        }
}

object CrashLogStore {

    private const val CRASH_DIR = "crash_logs"
    private const val MAX_LOGS = 20
    private const val TIMESTAMP_PREFIX = "Timestamp: "
    private const val THREAD_PREFIX = "Thread: "
    private const val MESSAGE_PREFIX = "Message: "
    private const val STACK_SEPARATOR = "---STACK TRACE---"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                save(appContext, thread, throwable)
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun save(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, CRASH_DIR).also { it.mkdirs() }
        val timestamp = System.currentTimeMillis()
        val file = File(dir, "crash_$timestamp.txt")
        file.writeText(buildString {
            appendLine("$TIMESTAMP_PREFIX$timestamp")
            appendLine("$THREAD_PREFIX${thread.name}")
            appendLine("$MESSAGE_PREFIX${throwable.message ?: throwable.javaClass.name}")
            appendLine(STACK_SEPARATOR)
            append(throwable.stackTraceToString())
        })
        rotate(dir)
    }

    private fun rotate(dir: File) {
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash_") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOGS)
            ?.forEach { it.delete() }
    }

    fun getAll(context: Context): List<CrashEntry> {
        val dir = File(context.filesDir, CRASH_DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash_") }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { parseFile(it) }
            ?: emptyList()
    }

    private fun parseFile(file: File): CrashEntry? = runCatching {
        val lines = file.readLines()
        val timestamp = lines.firstOrNull { it.startsWith(TIMESTAMP_PREFIX) }
            ?.removePrefix(TIMESTAMP_PREFIX)?.trim()?.toLongOrNull()
            ?: file.lastModified()
        val thread = lines.firstOrNull { it.startsWith(THREAD_PREFIX) }
            ?.removePrefix(THREAD_PREFIX)?.trim() ?: "unknown"
        val message = lines.firstOrNull { it.startsWith(MESSAGE_PREFIX) }
            ?.removePrefix(MESSAGE_PREFIX)?.trim() ?: "Unknown error"
        val stackStart = lines.indexOfFirst { it == STACK_SEPARATOR }
        val stackTrace = if (stackStart >= 0) lines.drop(stackStart + 1).joinToString("\n") else ""
        CrashEntry(
            id = file.name,
            timestamp = timestamp,
            threadName = thread,
            message = message,
            stackTrace = stackTrace
        )
    }.getOrNull()

    fun clearAll(context: Context) {
        File(context.filesDir, CRASH_DIR).deleteRecursively()
    }

    fun delete(context: Context, id: String) {
        File(File(context.filesDir, CRASH_DIR), id).takeIf { it.exists() }?.delete()
    }
}
