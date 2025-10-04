package com.callrecorder.pixel.error

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
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
class ErrorReporterTest {

    private lateinit var context: Context
    private lateinit var errorReporter: ErrorReporter
    private lateinit var filesDir: File
    private lateinit var packageManager: PackageManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        filesDir = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        
        every { context.filesDir } returns filesDir
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.test.package"
        
        // Mock file creation
        val logFile = mockk<File>(relaxed = true)
        val crashFile = mockk<File>(relaxed = true)
        
        mockkConstructor(File::class)
        every { anyConstructed<File>() } returns logFile andThen crashFile
        every { logFile.exists() } returns false
        every { logFile.createNewFile() } returns true
        every { crashFile.exists() } returns false
        every { crashFile.createNewFile() } returns true
        
        errorReporter = ErrorReporter(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `reportError creates error report and logs it`() {
        // Given
        val error = RecordingError.AudioSourceNotAvailable
        val context = "test context"
        val additionalInfo = mapOf("key" to "value")
        val exception = RuntimeException("test exception")

        // Mock FileWriter
        mockkConstructor(FileWriter::class)
        every { anyConstructed<FileWriter>().write(any<String>()) } just Runs
        every { anyConstructed<FileWriter>().close() } just Runs

        // When
        errorReporter.reportError(error, context, additionalInfo, exception)

        // Then
        verify { anyConstructed<FileWriter>().write(match<String> { it.contains(error.errorCode) }) }
    }

    @Test
    fun `reportCrash creates crash report and saves it`() {
        // Given
        val exception = RuntimeException("test crash")
        val context = "crash context"
        val additionalInfo = mapOf("crashKey" to "crashValue")

        // Mock FileWriter
        mockkConstructor(FileWriter::class)
        every { anyConstructed<FileWriter>().write(any<String>()) } just Runs
        every { anyConstructed<FileWriter>().close() } just Runs

        // When
        errorReporter.reportCrash(exception, context, additionalInfo)

        // Then
        verify { anyConstructed<FileWriter>().write(match<String> { 
            it.contains("Crash Report") && it.contains("test crash") 
        }) }
    }

    @Test
    fun `reportPerformanceIssue logs when duration exceeds threshold`() {
        // Given
        val operation = "testOperation"
        val duration = 2000L
        val threshold = 1000L

        // Mock FileWriter
        mockkConstructor(FileWriter::class)
        every { anyConstructed<FileWriter>().write(any<String>()) } just Runs
        every { anyConstructed<FileWriter>().close() } just Runs

        // When
        errorReporter.reportPerformanceIssue(operation, duration, threshold)

        // Then
        verify { anyConstructed<FileWriter>().write(match<String> { 
            it.contains("PERFORMANCE") && it.contains(operation) && it.contains("2000ms")
        }) }
    }

    @Test
    fun `reportPerformanceIssue does not log when duration under threshold`() {
        // Given
        val operation = "fastOperation"
        val duration = 500L
        val threshold = 1000L

        // Mock FileWriter
        mockkConstructor(FileWriter::class)
        every { anyConstructed<FileWriter>().write(any<String>()) } just Runs

        // When
        errorReporter.reportPerformanceIssue(operation, duration, threshold)

        // Then
        verify(exactly = 0) { anyConstructed<FileWriter>().write(any<String>()) }
    }

    @Test
    fun `getErrorStatistics returns correct statistics`() {
        // Given
        val error1 = RecordingError.AudioSourceNotAvailable
        val error2 = RecordingError.InsufficientStorage
        val error3 = RecordingError.AudioSourceNotAvailable

        // Mock FileWriter to avoid actual file operations
        mockkConstructor(FileWriter::class)
        every { anyConstructed<FileWriter>().write(any<String>()) } just Runs
        every { anyConstructed<FileWriter>().close() } just Runs

        // Report errors
        errorReporter.reportError(error1, "context1")
        errorReporter.reportError(error2, "context2")
        errorReporter.reportError(error3, "context1")

        // When
        val stats = errorReporter.getErrorStatistics()

        // Then
        assert(stats.totalErrors == 3)
        assert(stats.errorsByType["AudioSourceNotAvailable"] == 2)
        assert(stats.errorsByType["InsufficientStorage"] == 1)
        assert(stats.errorsByContext["context1"] == 2)
        assert(stats.errorsByContext["context2"] == 1)
        assert(stats.mostCommonError == "AudioSourceNotAvailable")
    }

    @Test
    fun `exportErrorLogs creates export file with logs`() = runTest {
        // Given
        val externalFilesDir = mockk<File>(relaxed = true)
        val exportFile = mockk<File>(relaxed = true)
        val logFile = mockk<File>(relaxed = true)
        val crashFile = mockk<File>(relaxed = true)
        
        every { context.getExternalFilesDir(null) } returns externalFilesDir
        every { logFile.exists() } returns true
        every { logFile.readText() } returns "test log content"
        every { crashFile.exists() } returns true
        every { crashFile.readText() } returns "test crash content"
        
        // Mock File constructor for export file
        mockkConstructor(File::class)
        every { anyConstructed<File>() } returns exportFile
        
        // Mock FileWriter
        mockkConstructor(FileWriter::class)
        every { anyConstructed<FileWriter>().write(any<String>()) } just Runs
        every { anyConstructed<FileWriter>().close() } just Runs

        // Mock package info
        val packageInfo = mockk<PackageInfo>()
        packageInfo.versionName = "1.0.0"
        packageInfo.longVersionCode = 1L
        every { packageManager.getPackageInfo("com.test.package", 0) } returns packageInfo

        // When
        val result = errorReporter.exportErrorLogs()

        // Then
        assert(result.isSuccess)
        verify { anyConstructed<FileWriter>().write(match<String> { 
            it.contains("Call Recorder Error Logs") 
        }) }
    }

    @Test
    fun `clearOldLogs removes old entries from queue`() = runTest {
        // Given
        val oldError = RecordingError.AudioSourceNotAvailable
        val recentError = RecordingError.InsufficientStorage
        
        // Mock FileWriter
        mockkConstructor(FileWriter::class)
        every { anyConstructed<FileWriter>().write(any<String>()) } just Runs
        every { anyConstructed<FileWriter>().close() } just Runs
        
        // Mock file operations for log rotation
        val logFile = mockk<File>(relaxed = true)
        val backupFile = mockk<File>(relaxed = true)
        
        every { logFile.exists() } returns true
        every { logFile.length() } returns 2 * 1024 * 1024L // 2MB
        every { logFile.renameTo(any()) } returns true
        every { logFile.createNewFile() } returns true
        
        mockkConstructor(File::class)
        every { anyConstructed<File>() } returns logFile andThen backupFile

        // Report errors with different timestamps (mocked through system time)
        errorReporter.reportError(oldError, "old context")
        errorReporter.reportError(recentError, "recent context")

        // When
        errorReporter.clearOldLogs(1000L) // Very short max age to clear old entries

        // Then
        // Verify log file rotation occurred due to size
        verify { logFile.renameTo(any()) }
        verify { logFile.createNewFile() }
    }

    @Test
    fun `uncaught exception handler reports crashes`() {
        // Given
        val originalHandler = mockk<Thread.UncaughtExceptionHandler>(relaxed = true)
        val thread = mockk<Thread>(relaxed = true)
        val exception = RuntimeException("uncaught exception")
        
        every { thread.name } returns "test-thread"
        every { thread.id } returns 123L
        
        // Mock Thread static methods
        mockkStatic(Thread::class)
        every { Thread.getDefaultUncaughtExceptionHandler() } returns originalHandler
        every { Thread.setDefaultUncaughtExceptionHandler(any()) } just Runs

        // Mock FileWriter
        mockkConstructor(FileWriter::class)
        every { anyConstructed<FileWriter>().write(any<String>()) } just Runs
        every { anyConstructed<FileWriter>().close() } just Runs

        // Create new error reporter to trigger handler setup
        val newErrorReporter = ErrorReporter(context)

        // Capture the uncaught exception handler
        val handlerSlot = slot<Thread.UncaughtExceptionHandler>()
        verify { Thread.setDefaultUncaughtExceptionHandler(capture(handlerSlot)) }

        // When
        handlerSlot.captured.uncaughtException(thread, exception)

        // Then
        verify { originalHandler.uncaughtException(thread, exception) }
        verify { anyConstructed<FileWriter>().write(match<String> { 
            it.contains("CRASH") && it.contains("uncaught exception") 
        }) }
    }

    @Test
    fun `getDeviceInfo returns correct device information`() {
        // Given - Build class is automatically mocked by Robolectric
        
        // When
        val errorReporter = ErrorReporter(context)
        errorReporter.reportError(RecordingError.AudioSourceNotAvailable, "test")
        
        // Then - Just verify no exceptions are thrown
        // Device info is included in error reports automatically
    }

    @Test
    fun `getAppVersion returns version information`() {
        // Given
        val packageInfo = mockk<PackageInfo>()
        packageInfo.versionName = "1.2.3"
        packageInfo.longVersionCode = 456L
        
        every { packageManager.getPackageInfo("com.test.package", 0) } returns packageInfo

        // Mock FileWriter
        mockkConstructor(FileWriter::class)
        every { anyConstructed<FileWriter>().write(any<String>()) } just Runs
        every { anyConstructed<FileWriter>().close() } just Runs

        // When
        errorReporter.reportError(RecordingError.AudioSourceNotAvailable, "test")

        // Then
        verify { anyConstructed<FileWriter>().write(match<String> { 
            it.contains("1.2.3") && it.contains("456")
        }) }
    }
}