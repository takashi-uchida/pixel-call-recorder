package com.callrecorder.pixel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.CallInfo
import java.io.File
import java.time.LocalDateTime

/**
 * Utility functions and extensions for testing
 */
object TestUtils {
    
    fun getTestContext(): Context = ApplicationProvider.getApplicationContext()
    
    fun createTestFile(name: String = "test_recording.wav"): File {
        val context = getTestContext()
        return File(context.cacheDir, name).apply {
            if (!exists()) {
                createNewFile()
                writeText("test audio data")
            }
        }
    }
    
    fun createTestCallInfo(): CallInfo {
        return CallInfo(
            callId = "test_call_123",
            phoneNumber = "+1234567890",
            contactName = "Test Contact",
            isIncoming = true,
            startTime = LocalDateTime.now()
        )
    }
    
    fun getTestAudioQuality(): AudioQuality = AudioQuality.STANDARD
    
    fun cleanupTestFiles() {
        val context = getTestContext()
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("test_")) {
                file.delete()
            }
        }
    }
}

/**
 * Extension function to run suspend functions in tests
 */
fun runSuspendTest(block: suspend () -> Unit) = runTest {
    block()
}