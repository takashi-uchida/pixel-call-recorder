package com.callrecorder.pixel.logging

import android.content.Context
import android.util.Log
import com.callrecorder.pixel.error.ErrorReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Comprehensive logging system for the Call Recorder application.
 * Provides structured logging with different levels, file output, and integration with error reporting.
 */
class Logger private constructor(private val context: Context) {

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logFile = File(context.filesDir, "app_logs.txt")
    private val debugLogFile = File(context.filesDir, "debug_logs.txt")
    private var errorReporter: ErrorReporter? = null
    private var isDebugMode = false

    companion object {
        @Volatile
        private var INSTANCE: Logger? = null
        private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024L // 5MB
        private const val MAX_QUEUE_SIZE = 1000

        fun getInstance(context: Context): Logger {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Logger(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Convenience methods for global logging
        fun d(tag: String, message: String, throwable: Throwable? = null) {
            INSTANCE?.debug(tag, message, throwable)
        }

        fun i(tag: String, message: String, throwable: Throwable? = null) {
            INSTANCE?.info(tag, message, throwable)
        }

        fun w(tag: String, message: String, throwable: Throwable? = null) {
            INSTANCE?.warn(tag, message, throwable)
        }

        fun e(tag: String, message: String, throwable: Throwable? = null) {
            INSTANCE?.error(tag, message, throwable)
        }

        fun v(tag: String, message: String, throwable: Throwable? = null) {
            INSTANCE?.verbose(tag, message, throwable)
        }
    }

    init {
        ensureLogFilesExist()
        setupLogRotation()
    }

    /**
     * Sets the error reporter for integration with error handling
     */
    fun setErrorReporter(errorReporter: ErrorReporter) {
        this.errorReporter = errorReporter
    }

    /**
     * Enables or disables debug mode
     */
    fun setDebugMode(enabled: Boolean) {
        isDebugMode = enabled
        if (enabled) {
            info("Logger", "Debug mode enabled")
        }
    }

    /**
     * Logs a verbose message
     */
    fun verbose(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.VERBOSE, tag, message, throwable)
    }

    /**
     * Logs a debug message
     */
    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, tag, message, throwable)
    }

    /**
     * Logs an info message
     */
    fun info(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, tag, message, throwable)
    }

    /**
     * Logs a warning message
     */
    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    /**
     * Logs an error message
     */
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
        
        // Report to error reporter if available
        throwable?.let { 
            errorReporter?.reportCrash(it, "Logger error: $tag - $message")
        }
    }

    /**
     * Logs a method entry for debugging
     */
    fun methodEntry(tag: String, methodName: String, vararg params: Any) {
        if (isDebugMode) {
            val paramString = if (params.isNotEmpty()) {
                " with params: ${params.joinToString(", ")}"
            } else {
                ""
            }
            debug(tag, "→ $methodName$paramString")
        }
    }

    /**
     * Logs a method exit for debugging
     */
    fun methodExit(tag: String, methodName: String, result: Any? = null) {
        if (isDebugMode) {
            val resultString = result?.let { " returning: $it" } ?: ""
            debug(tag, "← $methodName$resultString")
        }
    }

    /**
     * Logs performance metrics
     */
    fun performance(tag: String, operation: String, duration: Long, additionalInfo: Map<String, Any> = emptyMap()) {
        val infoString = if (additionalInfo.isNotEmpty()) {
            " | ${additionalInfo.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else {
            ""
        }
        info(tag, "PERF: $operation took ${duration}ms$infoString")
        
        // Report to error reporter for performance monitoring
        errorReporter?.reportPerformanceIssue(operation, duration, additionalInfo = additionalInfo)
    }

    /**
     * Logs user actions for analytics
     */
    fun userAction(tag: String, action: String, details: Map<String, Any> = emptyMap()) {
        val detailString = if (details.isNotEmpty()) {
            " | ${details.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else {
            ""
        }
        info(tag, "USER: $action$detailString")
    }

    /**
     * Logs system state information
     */
    fun systemState(tag: String, state: String, details: Map<String, Any> = emptyMap()) {
        val detailString = if (details.isNotEmpty()) {
            " | ${details.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else {
            ""
        }
        info(tag, "STATE: $state$detailString")
    }

    /**
     * Core logging method
     */
    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = System.currentTimeMillis()
        val logEntry = LogEntry(
            level = level,
            tag = tag,
            message = message,
            throwable = throwable,
            timestamp = timestamp,
            threadName = Thread.currentThread().name
        )

        // Add to queue
        if (logQueue.size >= MAX_QUEUE_SIZE) {
            logQueue.poll() // Remove oldest entry
        }
        logQueue.offer(logEntry)

        // Log to Android Log
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message, throwable)
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }

        // Write to file asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            writeToFile(logEntry)
        }
    }

    /**
     * Writes log entry to file
     */
    private suspend fun writeToFile(logEntry: LogEntry) {
        try {
            val logText = formatLogEntry(logEntry)
            
            // Write to main log file
            if (logEntry.level != LogLevel.VERBOSE && logEntry.level != LogLevel.DEBUG) {
                appendToFile(logFile, logText)
            }
            
            // Write to debug log file if debug mode or debug/verbose level
            if (isDebugMode || logEntry.level == LogLevel.DEBUG || logEntry.level == LogLevel.VERBOSE) {
                appendToFile(debugLogFile, logText)
            }
            
        } catch (e: Exception) {
            Log.e("Logger", "Failed to write log to file", e)
        }
    }

    /**
     * Formats a log entry for file output
     */
    private fun formatLogEntry(logEntry: LogEntry): String {
        return buildString {
            append("[${dateFormat.format(Date(logEntry.timestamp))}] ")
            append("${logEntry.level.name.first()}/${logEntry.tag}: ")
            append(logEntry.message)
            append(" [${logEntry.threadName}]")
            
            logEntry.throwable?.let { throwable ->
                append("\n")
                append(getStackTrace(throwable))
            }
            
            append("\n")
        }
    }

    /**
     * Gets stack trace as string
     */
    private fun getStackTrace(throwable: Throwable): String {
        return try {
            val sw = java.io.StringWriter()
            val pw = java.io.PrintWriter(sw)
            throwable.printStackTrace(pw)
            sw.toString()
        } catch (e: Exception) {
            "Failed to get stack trace: ${e.message}"
        }
    }

    /**
     * Appends text to file
     */
    private fun appendToFile(file: File, content: String) {
        try {
            FileWriter(file, true).use { writer ->
                writer.write(content)
            }
        } catch (e: Exception) {
            Log.e("Logger", "Failed to write to log file: ${file.name}", e)
        }
    }

    /**
     * Ensures log files exist
     */
    private fun ensureLogFilesExist() {
        try {
            if (!logFile.exists()) {
                logFile.createNewFile()
            }
            if (!debugLogFile.exists()) {
                debugLogFile.createNewFile()
            }
        } catch (e: Exception) {
            Log.e("Logger", "Failed to create log files", e)
        }
    }

    /**
     * Sets up log file rotation
     */
    private fun setupLogRotation() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rotateLogFileIfNeeded(logFile)
                rotateLogFileIfNeeded(debugLogFile)
            } catch (e: Exception) {
                Log.e("Logger", "Failed to rotate log files", e)
            }
        }
    }

    /**
     * Rotates log file if it exceeds maximum size
     */
    private fun rotateLogFileIfNeeded(file: File) {
        if (file.exists() && file.length() > MAX_LOG_FILE_SIZE) {
            val backupFile = File(file.parent, "${file.nameWithoutExtension}_backup.${file.extension}")
            
            // Delete old backup if exists
            if (backupFile.exists()) {
                backupFile.delete()
            }
            
            // Rename current file to backup
            file.renameTo(backupFile)
            
            // Create new log file
            file.createNewFile()
            
            Log.i("Logger", "Rotated log file: ${file.name}")
        }
    }

    /**
     * Gets recent log entries
     */
    fun getRecentLogs(maxEntries: Int = 100): List<LogEntry> {
        return logQueue.toList().takeLast(maxEntries)
    }

    /**
     * Gets logs filtered by level
     */
    fun getLogsByLevel(level: LogLevel, maxEntries: Int = 100): List<LogEntry> {
        return logQueue.filter { it.level == level }.takeLast(maxEntries)
    }

    /**
     * Gets logs filtered by tag
     */
    fun getLogsByTag(tag: String, maxEntries: Int = 100): List<LogEntry> {
        return logQueue.filter { it.tag == tag }.takeLast(maxEntries)
    }

    /**
     * Exports logs to external file
     */
    suspend fun exportLogs(): File? {
        return try {
            val exportFile = File(context.getExternalFilesDir(null), "call_recorder_logs_${System.currentTimeMillis()}.txt")
            
            FileWriter(exportFile).use { writer ->
                writer.write("Call Recorder Application Logs\n")
                writer.write("Generated: ${dateFormat.format(Date())}\n")
                writer.write("Debug Mode: $isDebugMode\n")
                writer.write("=".repeat(50) + "\n\n")

                // Write main logs
                if (logFile.exists()) {
                    writer.write("MAIN LOGS:\n")
                    writer.write("-".repeat(30) + "\n")
                    logFile.readText().let { writer.write(it) }
                    writer.write("\n\n")
                }

                // Write debug logs if available
                if (debugLogFile.exists() && isDebugMode) {
                    writer.write("DEBUG LOGS:\n")
                    writer.write("-".repeat(30) + "\n")
                    debugLogFile.readText().let { writer.write(it) }
                }
            }

            exportFile
        } catch (e: Exception) {
            Log.e("Logger", "Failed to export logs", e)
            null
        }
    }

    /**
     * Clears all logs
     */
    suspend fun clearLogs() {
        try {
            logQueue.clear()
            
            if (logFile.exists()) {
                logFile.delete()
                logFile.createNewFile()
            }
            
            if (debugLogFile.exists()) {
                debugLogFile.delete()
                debugLogFile.createNewFile()
            }
            
            info("Logger", "All logs cleared")
        } catch (e: Exception) {
            Log.e("Logger", "Failed to clear logs", e)
        }
    }

    /**
     * Gets log statistics
     */
    fun getLogStatistics(): LogStatistics {
        val logs = logQueue.toList()
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (60 * 60 * 1000L)
        
        val recentLogs = logs.filter { it.timestamp >= oneHourAgo }
        val logsByLevel = logs.groupBy { it.level }.mapValues { it.value.size }
        val logsByTag = logs.groupBy { it.tag }.mapValues { it.value.size }
        val errorCount = logs.count { it.level == LogLevel.ERROR }
        val warningCount = logs.count { it.level == LogLevel.WARN }
        
        return LogStatistics(
            totalLogs = logs.size,
            recentLogs = recentLogs.size,
            logsByLevel = logsByLevel,
            logsByTag = logsByTag,
            errorCount = errorCount,
            warningCount = warningCount,
            logFileSize = if (logFile.exists()) logFile.length() else 0L,
            debugLogFileSize = if (debugLogFile.exists()) debugLogFile.length() else 0L
        )
    }
}

/**
 * Log levels
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * Log entry data class
 */
data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable?,
    val timestamp: Long,
    val threadName: String
)

/**
 * Log statistics data class
 */
data class LogStatistics(
    val totalLogs: Int,
    val recentLogs: Int,
    val logsByLevel: Map<LogLevel, Int>,
    val logsByTag: Map<String, Int>,
    val errorCount: Int,
    val warningCount: Int,
    val logFileSize: Long,
    val debugLogFileSize: Long
)
