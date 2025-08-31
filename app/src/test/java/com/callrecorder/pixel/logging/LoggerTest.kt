package com.callrecorder.pixel.logging

import android.content.Context
import android.util.Log
import com.callrecorder.pixel.error.ErrorReporter
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileWriter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LoggerTest {

    private lateinit var context: Context
    private lateinit var logger: Logger
    private lateinit var errorReporter: ErrorReporter

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        errorReporter = mockk(relaxed = true)
        
        val filesDir = mockk<File>(relaxed = true)
        every { context.filesDir } returns filesDir
        every { context.applicationContext } returns context

        // Mock file operations
        val logFile = mockk<File>(relaxed = true)
        val debugLogFile = mockk<File>(relaxed = true)
        
        mockkConstructor(File::class)
        every { anyConstructed<File>() } returns logFile andThen debugLogFile
        every { logFile.exists() } returns false
        every { logFile.createNewFile() } returns true
        every { debugLogFile.exists() } returns false
        every { debugLogFile.createNewFile() } returns true
        every { logFile.length() } returns 1024L
        every { debugLogFile.length() } returns 512L

        // Mock FileWriter
        mockkConstructor(FileWriter::class)
        every { anyConstructed<FileWriter>().write(any<String>()) } just Runs
        every { anyConstructed<FileWriter>().close() } just Runs

        // Mock Android Log
        mockkStatic(Log::class)
        every { Log.v(any(), any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
        every { Log.i(any(), any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        logger = Logger.getInstance(context)
        logger.setErrorReporter(errorReporter)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getInstance returns singleton instance`() {
        // Given
        val logger1 = Logger.getInstance(context)
        val logger2 = Logger.getInstance(context)

        // Then
        assert(logger1 === logger2)
    }

    @Test
    fun `debug logs message with DEBUG level`() {
        // Given
        val tag = "TestTag"
        val message = "Test debug message"

        // When
        logger.debug(tag, message)

        // Then
        verify { Log.d(tag, message, null) }
    }

    @Test
    fun `info logs message with INFO level`() {
        // Given
        val tag = "TestTag"
        val message = "Test info message"

        // When
        logger.info(tag, message)

        // Then
        verify { Log.i(tag, message, null) }
    }

    @Test
    fun `warn logs message with WARN level`() {
        // Given
        val tag = "TestTag"
        val message = "Test warning message"

        // When
        logger.warn(tag, message)

        // Then
        verify { Log.w(tag, message, null) }
    }

    @Test
    fun `error logs message and reports to error reporter`() {
        // Given
        val tag = "TestTag"
        val message = "Test error message"
        val throwable = RuntimeException("Test exception")

        // When
        logger.error(tag, message, throwable)

        // Then
        verify { Log.e(tag, message, throwable) }
        verify { errorReporter.reportCrash(throwable, "Logger error: $tag - $message") }
    }

    @Test
    fun `verbose logs message with VERBOSE level`() {
        // Given
        val tag = "TestTag"
        val message = "Test verbose message"

        // When
        logger.verbose(tag, message)

        // Then
        verify { Log.v(tag, message, null) }
    }

    @Test
    fun `methodEntry logs in debug mode`() {
        // Given
        logger.setDebugMode(true)
        val tag = "TestTag"
        val methodName = "testMethod"
        val params = arrayOf("param1", "param2")

        // When
        logger.methodEntry(tag, methodName, *params)

        // Then
        verify { Log.d(tag, "→ $methodName with params: param1, param2") }
    }

    @Test
    fun `methodEntry does not log when debug mode disabled`() {
        // Given
        logger.setDebugMode(false)
        val tag = "TestTag"
        val methodName = "testMethod"

        // When
        logger.methodEntry(tag, methodName)

        // Then
        verify(exactly = 0) { Log.d(tag, any()) }
    }

    @Test
    fun `methodExit logs in debug mode`() {
        // Given
        logger.setDebugMode(true)
        val tag = "TestTag"
        val methodName = "testMethod"
        val result = "testResult"

        // When
        logger.methodExit(tag, methodName, result)

        // Then
        verify { Log.d(tag, "← $methodName returning: $result") }
    }

    @Test
    fun `performance logs and reports to error reporter`() {
        // Given
        val tag = "TestTag"
        val operation = "testOperation"
        val duration = 1500L
        val additionalInfo = mapOf("key" to "value")

        // When
        logger.performance(tag, operation, duration, additionalInfo)

        // Then
        verify { Log.i(tag, "PERF: $operation took ${duration}ms | key=value") }
        verify { errorReporter.reportPerformanceIssue(operation, duration, additionalInfo = additionalInfo) }
    }

    @Test
    fun `userAction logs user actions`() {
        // Given
        val tag = "TestTag"
        val action = "buttonClick"
        val details = mapOf("button" to "record", "screen" to "main")

        // When
        logger.userAction(tag, action, details)

        // Then
        verify { Log.i(tag, "USER: $action | button=record, screen=main") }
    }

    @Test
    fun `systemState logs system state information`() {
        // Given
        val tag = "TestTag"
        val state = "recording"
        val details = mapOf("duration" to 30000L, "quality" to "high")

        // When
        logger.systemState(tag, state, details)

        // Then
        verify { Log.i(tag, "STATE: $state | duration=30000, quality=high") }
    }

    @Test
    fun `getRecentLogs returns recent log entries`() {
        // Given
        logger.info("Tag1", "Message 1")
        logger.warn("Tag2", "Message 2")
        logger.error("Tag3", "Message 3")

        // When
        val recentLogs = logger.getRecentLogs(2)

        // Then
        assert(recentLogs.size == 2)
        assert(recentLogs[0].message == "Message 2")
        assert(recentLogs[1].message == "Message 3")
    }

    @Test
    fun `getLogsByLevel filters logs by level`() {
        // Given
        logger.info("Tag1", "Info message")
        logger.warn("Tag2", "Warning message")
        logger.error("Tag3", "Error message")

        // When
        val errorLogs = logger.getLogsByLevel(LogLevel.ERROR)

        // Then
        assert(errorLogs.size == 1)
        assert(errorLogs[0].level == LogLevel.ERROR)
        assert(errorLogs[0].message == "Error message")
    }

    @Test
    fun `getLogsByTag filters logs by tag`() {
        // Given
        logger.info("Tag1", "Message 1")
        logger.warn("Tag1", "Message 2")
        logger.error("Tag2", "Message 3")

        // When
        val tag1Logs = logger.getLogsByTag("Tag1")

        // Then
        assert(tag1Logs.size == 2)
        assert(tag1Logs.all { it.tag == "Tag1" })
    }

    @Test
    fun `exportLogs creates export file`() = runTest {
        // Given
        val externalFilesDir = mockk<File>(relaxed = true)
        val exportFile = mockk<File>(relaxed = true)
        val logFile = mockk<File>(relaxed = true)
        val debugLogFile = mockk<File>(relaxed = true)
        
        every { context.getExternalFilesDir(null) } returns externalFilesDir
        every { logFile.exists() } returns true
        every { logFile.readText() } returns "test log content"
        every { debugLogFile.exists() } returns true
        every { debugLogFile.readText() } returns "test debug content"
        
        mockkConstructor(File::class)
        every { anyConstructed<File>() } returns exportFile

        // When
        val result = logger.exportLogs()

        // Then
        assert(result != null)
        verify { anyConstructed<FileWriter>().write(match<String> { 
            it.contains("Call Recorder Application Logs") 
        }) }
    }

    @Test
    fun `clearLogs removes all logs`() = runTest {
        // Given
        logger.info("Tag1", "Message 1")
        logger.warn("Tag2", "Message 2")
        
        val logFile = mockk<File>(relaxed = true)
        val debugLogFile = mockk<File>(relaxed = true)
        
        every { logFile.exists() } returns true
        every { logFile.delete() } returns true
        every { logFile.createNewFile() } returns true
        every { debugLogFile.exists() } returns true
        every { debugLogFile.delete() } returns true
        every { debugLogFile.createNewFile() } returns true

        // When
        logger.clearLogs()

        // Then
        val recentLogs = logger.getRecentLogs()
        // Should only contain the "All logs cleared" message
        assert(recentLogs.size == 1)
        assert(recentLogs[0].message == "All logs cleared")
    }

    @Test
    fun `getLogStatistics returns correct statistics`() {
        // Given
        logger.info("Tag1", "Info message")
        logger.warn("Tag2", "Warning message")
        logger.error("Tag3", "Error message")
        logger.error("Tag4", "Another error")

        // When
        val stats = logger.getLogStatistics()

        // Then
        assert(stats.totalLogs == 4)
        assert(stats.errorCount == 2)
        assert(stats.warningCount == 1)
        assert(stats.logsByLevel[LogLevel.INFO] == 1)
        assert(stats.logsByLevel[LogLevel.WARN] == 1)
        assert(stats.logsByLevel[LogLevel.ERROR] == 2)
    }

    @Test
    fun `setDebugMode enables debug logging`() {
        // Given
        val wasDebugMode = false

        // When
        logger.setDebugMode(true)

        // Then
        verify { Log.i("Logger", "Debug mode enabled") }
    }

    @Test
    fun `static convenience methods work correctly`() {
        // When
        Logger.d("TestTag", "Debug message")
        Logger.i("TestTag", "Info message")
        Logger.w("TestTag", "Warning message")
        Logger.e("TestTag", "Error message")
        Logger.v("TestTag", "Verbose message")

        // Then
        verify { Log.d("TestTag", "Debug message", null) }
        verify { Log.i("TestTag", "Info message", null) }
        verify { Log.w("TestTag", "Warning message", null) }
        verify { Log.e("TestTag", "Error message", null) }
        verify { Log.v("TestTag", "Verbose message", null) }
    }

    @Test
    fun `log queue maintains maximum size`() {
        // Given - Logger has MAX_QUEUE_SIZE = 1000
        // Add more than max size
        repeat(1100) { i ->
            logger.info("Tag", "Message $i")
        }

        // When
        val recentLogs = logger.getRecentLogs(1100)

        // Then
        // Should only return the last 1000 entries
        assert(recentLogs.size == 1000)
        assert(recentLogs.last().message == "Message 1099")
    }
}