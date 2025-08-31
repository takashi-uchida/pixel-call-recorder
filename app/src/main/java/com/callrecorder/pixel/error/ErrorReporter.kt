package com.callrecorder.pixel.error

import android.content.Context
import android.os.Build
import android.util.Log
import com.callrecorder.pixel.common.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Handles error reporting, logging, and crash reporting for the application.
 * Provides detailed error information for debugging and monitoring.
 */
class ErrorReporter(private val context: Context) {

    private val errorQueue = ConcurrentLinkedQueue<ErrorReport>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logFile = File(context.filesDir, "error_logs.txt")
    private val crashFile = File(context.filesDir, "crash_reports.txt")

    init {
        // Set up uncaught exception handler
        setupUncaughtExceptionHandler()
        
        // Ensure log files exist
        ensureLogFilesExist()
    }

    /**
     * Reports an error with context information
     */
    fun reportError(
        error: CallRecorderError,
        context: String = "",
        additionalInfo: Map<String, Any> = emptyMap(),
        exception: Throwable? = null
    ) {
        val errorReport = ErrorReport(
            error = error,
            context = context,
            additionalInfo = additionalInfo,
            exception = exception,
            timestamp = System.currentTimeMillis(),
            deviceInfo = getDeviceInfo(),
            appVersion = getAppVersion()
        )

        // Add to queue for processing
        errorQueue.offer(errorReport)

        // Log immediately
        logError(errorReport)

        // Process queue asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            processErrorQueue()
        }
    }

    /**
     * Reports a crash with full stack trace
     */
    fun reportCrash(
        exception: Throwable,
        context: String = "",
        additionalInfo: Map<String, Any> = emptyMap()
    ) {
        val crashReport = CrashReport(
            exception = exception,
            context = context,
            additionalInfo = additionalInfo,
            timestamp = System.currentTimeMillis(),
            deviceInfo = getDeviceInfo(),
            appVersion = getAppVersion(),
            stackTrace = getStackTrace(exception)
        )

        // Log crash immediately
        logCrash(crashReport)

        // Save crash report to file
        CoroutineScope(Dispatchers.IO).launch {
            saveCrashReport(crashReport)
        }
    }

    /**
     * Logs performance metrics and warnings
     */
    fun reportPerformanceIssue(
        operation: String,
        duration: Long,
        threshold: Long = 1000L,
        additionalInfo: Map<String, Any> = emptyMap()
    ) {
        if (duration > threshold) {
            val performanceReport = PerformanceReport(
                operation = operation,
                duration = duration,
                threshold = threshold,
                additionalInfo = additionalInfo,
                timestamp = System.currentTimeMillis(),
                deviceInfo = getDeviceInfo()
            )

            logPerformanceIssue(performanceReport)
        }
    }

    /**
     * Gets error statistics for monitoring
     */
    fun getErrorStatistics(timeRangeMs: Long = 24 * 60 * 60 * 1000L): ErrorStatistics {
        val currentTime = System.currentTimeMillis()
        val recentErrors = errorQueue.filter { 
            currentTime - it.timestamp <= timeRangeMs 
        }

        val errorsByType = recentErrors.groupBy { it.error::class.simpleName }
            .mapValues { it.value.size }

        val errorsByContext = recentErrors.groupBy { it.context }
            .mapValues { it.value.size }

        return ErrorStatistics(
            totalErrors = recentErrors.size,
            errorsByType = errorsByType,
            errorsByContext = errorsByContext,
            timeRangeMs = timeRangeMs,
            mostCommonError = errorsByType.maxByOrNull { it.value }?.key,
            errorRate = recentErrors.size.toFloat() / (timeRangeMs / (60 * 1000L)) // errors per minute
        )
    }

    /**
     * Exports error logs for debugging
     */
    suspend fun exportErrorLogs(): Result<File> {
        return try {
            val exportFile = File(context.getExternalFilesDir(null), "call_recorder_logs_${System.currentTimeMillis()}.txt")
            
            FileWriter(exportFile).use { writer ->
                writer.write("Call Recorder Error Logs\n")
                writer.write("Generated: ${dateFormat.format(Date())}\n")
                writer.write("Device: ${getDeviceInfo()}\n")
                writer.write("App Version: ${getAppVersion()}\n")
                writer.write("=".repeat(50) + "\n\n")

                // Write error logs
                if (logFile.exists()) {
                    writer.write("ERROR LOGS:\n")
                    writer.write("-".repeat(30) + "\n")
                    logFile.readText().let { writer.write(it) }
                    writer.write("\n\n")
                }

                // Write crash reports
                if (crashFile.exists()) {
                    writer.write("CRASH REPORTS:\n")
                    writer.write("-".repeat(30) + "\n")
                    crashFile.readText().let { writer.write(it) }
                }
            }

            Result.success(exportFile)
        } catch (e: Exception) {
            Result.error(StorageError.FileAccessDenied)
        }
    }

    /**
     * Clears old error logs to prevent excessive storage usage
     */
    suspend fun clearOldLogs(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L) {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Clear in-memory queue
            errorQueue.removeAll { currentTime - it.timestamp > maxAgeMs }
            
            // Rotate log files if they're too large
            if (logFile.exists() && logFile.length() > 1024 * 1024) { // 1MB
                val backupFile = File(context.filesDir, "error_logs_backup.txt")
                logFile.renameTo(backupFile)
                logFile.createNewFile()
            }
            
            if (crashFile.exists() && crashFile.length() > 1024 * 1024) { // 1MB
                val backupFile = File(context.filesDir, "crash_reports_backup.txt")
                crashFile.renameTo(backupFile)
                crashFile.createNewFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear old logs", e)
        }
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            reportCrash(
                exception = exception,
                context = "UncaughtException in thread: ${thread.name}",
                additionalInfo = mapOf(
                    "threadName" to thread.name,
                    "threadId" to thread.id
                )
            )
            
            // Call the default handler to maintain normal crash behavior
            defaultHandler?.uncaughtException(thread, exception)
        }
    }

    private fun ensureLogFilesExist() {
        try {
            if (!logFile.exists()) {
                logFile.createNewFile()
            }
            if (!crashFile.exists()) {
                crashFile.createNewFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create log files", e)
        }
    }

    private fun logError(errorReport: ErrorReport) {
        val logMessage = buildString {
            append("[${dateFormat.format(Date(errorReport.timestamp))}] ")
            append("ERROR: ${errorReport.error.errorCode} - ${errorReport.error.userMessage}")
            if (errorReport.context.isNotEmpty()) {
                append(" | Context: ${errorReport.context}")
            }
            if (errorReport.additionalInfo.isNotEmpty()) {
                append(" | Info: ${errorReport.additionalInfo}")
            }
            if (errorReport.exception != null) {
                append(" | Exception: ${errorReport.exception.message}")
            }
            append("\n")
        }

        Log.e(TAG, logMessage)
        appendToFile(logFile, logMessage)
    }

    private fun logCrash(crashReport: CrashReport) {
        val logMessage = buildString {
            append("[${dateFormat.format(Date(crashReport.timestamp))}] ")
            append("CRASH: ${crashReport.exception.javaClass.simpleName}")
            append(" - ${crashReport.exception.message}")
            if (crashReport.context.isNotEmpty()) {
                append(" | Context: ${crashReport.context}")
            }
            append("\n${crashReport.stackTrace}\n")
            append("-".repeat(50) + "\n")
        }

        Log.e(TAG, logMessage)
        appendToFile(crashFile, logMessage)
    }

    private fun logPerformanceIssue(performanceReport: PerformanceReport) {
        val logMessage = buildString {
            append("[${dateFormat.format(Date(performanceReport.timestamp))}] ")
            append("PERFORMANCE: ${performanceReport.operation} took ${performanceReport.duration}ms ")
            append("(threshold: ${performanceReport.threshold}ms)")
            if (performanceReport.additionalInfo.isNotEmpty()) {
                append(" | Info: ${performanceReport.additionalInfo}")
            }
            append("\n")
        }

        Log.w(TAG, logMessage)
        appendToFile(logFile, logMessage)
    }

    private fun processErrorQueue() {
        // Process any queued errors
        while (errorQueue.isNotEmpty()) {
            val errorReport = errorQueue.poll()
            if (errorReport != null) {
                // Here you could send to analytics service, crash reporting service, etc.
                // For now, we just ensure it's logged
            }
        }
    }

    private fun saveCrashReport(crashReport: CrashReport) {
        try {
            val reportText = buildString {
                append("Crash Report\n")
                append("Timestamp: ${dateFormat.format(Date(crashReport.timestamp))}\n")
                append("App Version: ${crashReport.appVersion}\n")
                append("Device Info: ${crashReport.deviceInfo}\n")
                append("Context: ${crashReport.context}\n")
                append("Additional Info: ${crashReport.additionalInfo}\n")
                append("Exception: ${crashReport.exception.javaClass.simpleName}\n")
                append("Message: ${crashReport.exception.message}\n")
                append("Stack Trace:\n${crashReport.stackTrace}\n")
                append("=".repeat(80) + "\n\n")
            }

            appendToFile(crashFile, reportText)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash report", e)
        }
    }

    private fun appendToFile(file: File, content: String) {
        try {
            FileWriter(file, true).use { writer ->
                writer.write(content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file: ${file.name}", e)
        }
    }

    private fun getDeviceInfo(): String {
        return buildString {
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            append(" | OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            append(" | Build: ${Build.FINGERPRINT}")
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getStackTrace(exception: Throwable): String {
        return try {
            val sw = java.io.StringWriter()
            val pw = PrintWriter(sw)
            exception.printStackTrace(pw)
            sw.toString()
        } catch (e: Exception) {
            "Failed to get stack trace: ${e.message}"
        }
    }

    companion object {
        private const val TAG = "ErrorReporter"
    }
}

/**
 * Data classes for error reporting
 */
data class ErrorReport(
    val error: CallRecorderError,
    val context: String,
    val additionalInfo: Map<String, Any>,
    val exception: Throwable?,
    val timestamp: Long,
    val deviceInfo: String,
    val appVersion: String
)

data class CrashReport(
    val exception: Throwable,
    val context: String,
    val additionalInfo: Map<String, Any>,
    val timestamp: Long,
    val deviceInfo: String,
    val appVersion: String,
    val stackTrace: String
)

data class PerformanceReport(
    val operation: String,
    val duration: Long,
    val threshold: Long,
    val additionalInfo: Map<String, Any>,
    val timestamp: Long,
    val deviceInfo: String
)

data class ErrorStatistics(
    val totalErrors: Int,
    val errorsByType: Map<String?, Int>,
    val errorsByContext: Map<String, Int>,
    val timeRangeMs: Long,
    val mostCommonError: String?,
    val errorRate: Float
)
