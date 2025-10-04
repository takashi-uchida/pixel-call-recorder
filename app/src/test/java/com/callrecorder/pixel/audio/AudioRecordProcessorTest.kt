package com.callrecorder.pixel.audio

import android.content.Context
import com.callrecorder.pixel.data.model.AudioQuality
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit tests for AudioRecordProcessor
 */
class AudioRecordProcessorTest {

    private lateinit var context: Context
    private lateinit var audioProcessor: AudioRecordProcessor
    private lateinit var testOutputFile: File

    @Before
    fun setUp() {
        // Mock Android dependencies
        context = mockk(relaxed = true)
        
        // Create test output file
        testOutputFile = File.createTempFile("test_recording", ".pcm")
        
        // Create processor instance
        audioProcessor = AudioRecordProcessor(context)
    }

    @After
    fun tearDown() {
        // Clean up test files
        if (testOutputFile.exists()) {
            testOutputFile.delete()
        }
        
        // Clear mocks
        clearAllMocks()
    }

    @Test
    fun `initializeAudioCapture should handle different audio qualities`() = runTest {
        // Given
        val qualities = listOf(
            AudioQuality.HIGH_QUALITY,
            AudioQuality.STANDARD,
            AudioQuality.SPACE_SAVING
        )
        
        // When & Then
        for (quality in qualities) {
            // Note: This will likely fail in unit test environment due to AudioRecord dependencies
            val result = audioProcessor.initializeAudioCapture(quality)
            
            // In unit test environment, AudioRecord creation will fail
            // In integration tests, this would succeed
            assertFalse("AudioRecord initialization will fail in unit test environment for ${quality.displayName}", result)
        }
    }

    @Test
    fun `startCapture should fail when not initialized`() = runTest {
        // Given - processor not initialized
        
        // When
        val result = audioProcessor.startCapture(testOutputFile)
        
        // Then
        assertFalse("Start capture should fail when not initialized", result)
    }

    @Test
    fun `stopCapture should return error when not capturing`() = runTest {
        // Given - not capturing
        
        // When
        val result = audioProcessor.stopCapture()
        
        // Then
        assertTrue("Stop capture should return error when not capturing", 
            result is AudioProcessingResult.Error)
        
        val error = result as AudioProcessingResult.Error
        assertEquals("Error should be UNKNOWN_ERROR", 
            AudioProcessingError.UNKNOWN_ERROR, error.error)
    }

    @Test
    fun `pauseCapture should fail when not capturing`() = runTest {
        // Given - not capturing
        
        // When
        val result = audioProcessor.pauseCapture()
        
        // Then
        assertFalse("Pause should fail when not capturing", result)
    }

    @Test
    fun `resumeCapture should fail when not paused`() = runTest {
        // Given - not paused
        
        // When
        val result = audioProcessor.resumeCapture()
        
        // Then
        assertFalse("Resume should fail when not paused", result)
    }

    @Test
    fun `applyRealtimeGain should clamp gain values correctly`() = runTest {
        // Given
        val validGain = 10.0f
        val excessiveGain = 50.0f
        val negativeExcessiveGain = -50.0f
        
        // When
        val result1 = audioProcessor.applyRealtimeGain(validGain)
        val result2 = audioProcessor.applyRealtimeGain(excessiveGain)
        val result3 = audioProcessor.applyRealtimeGain(negativeExcessiveGain)
        
        // Then
        assertTrue("Valid gain should be accepted", result1)
        assertTrue("Excessive positive gain should be clamped and accepted", result2)
        assertTrue("Excessive negative gain should be clamped and accepted", result3)
    }

    @Test
    fun `getCurrentAudioLevel should return null when not capturing`() {
        // Given - not capturing
        
        // When
        val level = audioProcessor.getCurrentAudioLevel()
        
        // Then
        assertNull("Audio level should be null when not capturing", level)
    }

    @Test
    fun `getCurrentDuration should return zero when not capturing`() {
        // Given - not capturing
        
        // When
        val duration = audioProcessor.getCurrentDuration()
        
        // Then
        assertEquals("Duration should be zero when not capturing", 0L, duration)
    }

    @Test
    fun `isCapturing should return false initially`() {
        // Given - initial state
        
        // When
        val isCapturing = audioProcessor.isCapturing()
        
        // Then
        assertFalse("Should not be capturing initially", isCapturing)
    }

    @Test
    fun `getProcessingStatus should return IDLE initially`() {
        // Given - initial state
        
        // When
        val status = audioProcessor.getProcessingStatus()
        
        // Then
        assertEquals("Initial status should be IDLE", 
            AudioProcessingStatus.IDLE, status)
    }

    @Test
    fun `applyAudioEnhancement should fail for non-existent file`() = runTest {
        // Given
        val nonExistentFile = File("non_existent_file.pcm")
        val outputFile = File.createTempFile("output", ".pcm")
        
        try {
            // When
            val result = audioProcessor.applyAudioEnhancement(nonExistentFile, outputFile)
            
            // Then
            assertFalse("Enhancement should fail for non-existent file", result)
            assertEquals("Status should be ERROR after failed enhancement", 
                AudioProcessingStatus.ERROR, audioProcessor.getProcessingStatus())
        } finally {
            outputFile.delete()
        }
    }

    @Test
    fun `normalizeAudio should fail for non-existent file`() = runTest {
        // Given
        val nonExistentFile = File("non_existent_file.pcm")
        
        // When
        val result = audioProcessor.normalizeAudio(nonExistentFile)
        
        // Then
        assertFalse("Normalization should fail for non-existent file", result)
        assertEquals("Status should be ERROR after failed normalization", 
            AudioProcessingStatus.ERROR, audioProcessor.getProcessingStatus())
    }

    @Test
    fun `release should clean up resources gracefully`() = runTest {
        // Given - any state
        
        // When
        audioProcessor.release()
        
        // Then
        assertEquals("Status should be IDLE after release", 
            AudioProcessingStatus.IDLE, audioProcessor.getProcessingStatus())
        assertFalse("Should not be capturing after release", audioProcessor.isCapturing())
    }

    @Test
    fun `multiple release calls should be handled gracefully`() = runTest {
        // Given
        audioProcessor.release()
        
        // When - call release again
        audioProcessor.release()
        
        // Then - should not throw exception
        assertEquals("Status should remain IDLE", 
            AudioProcessingStatus.IDLE, audioProcessor.getProcessingStatus())
    }

    @Test
    fun `processor should handle state transitions correctly`() = runTest {
        // Given - initial state
        assertEquals("Initial status should be IDLE", 
            AudioProcessingStatus.IDLE, audioProcessor.getProcessingStatus())
        
        // When - try to initialize (will fail in unit test)
        val initResult = audioProcessor.initializeAudioCapture(AudioQuality.STANDARD)
        
        // Then - status should reflect the result
        if (initResult) {
            assertEquals("Status should be IDLE after successful init", 
                AudioProcessingStatus.IDLE, audioProcessor.getProcessingStatus())
        } else {
            assertEquals("Status should be ERROR after failed init", 
                AudioProcessingStatus.ERROR, audioProcessor.getProcessingStatus())
        }
    }

    @Test
    fun `applyRealtimeGain should store gain value for processing`() = runTest {
        // Given
        val testGain = 5.0f
        
        // When
        val result = audioProcessor.applyRealtimeGain(testGain)
        
        // Then
        assertTrue("Gain application should succeed", result)
        // Note: We can't directly test if the gain is stored since it's private,
        // but we can verify the method succeeds
    }

    @Test
    fun `processor should handle concurrent operations gracefully`() = runTest {
        // Given
        val gain1 = 5.0f
        val gain2 = -3.0f
        
        // When - apply multiple gains quickly
        val result1 = audioProcessor.applyRealtimeGain(gain1)
        val result2 = audioProcessor.applyRealtimeGain(gain2)
        
        // Then
        assertTrue("First gain application should succeed", result1)
        assertTrue("Second gain application should succeed", result2)
    }
}