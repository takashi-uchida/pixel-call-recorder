package com.callrecorder.pixel.audio

import com.callrecorder.pixel.data.model.AudioQuality
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit tests for audio processing interfaces, enums, and data classes
 */
class AudioProcessingTest {

    @Test
    fun `AudioProcessingStatus enum should have all expected values`() {
        // Given & When
        val statuses = AudioProcessingStatus.values()
        
        // Then
        assertTrue("Should contain IDLE", statuses.contains(AudioProcessingStatus.IDLE))
        assertTrue("Should contain INITIALIZING", statuses.contains(AudioProcessingStatus.INITIALIZING))
        assertTrue("Should contain CAPTURING", statuses.contains(AudioProcessingStatus.CAPTURING))
        assertTrue("Should contain PAUSED", statuses.contains(AudioProcessingStatus.PAUSED))
        assertTrue("Should contain PROCESSING", statuses.contains(AudioProcessingStatus.PROCESSING))
        assertTrue("Should contain ENHANCING", statuses.contains(AudioProcessingStatus.ENHANCING))
        assertTrue("Should contain FINALIZING", statuses.contains(AudioProcessingStatus.FINALIZING))
        assertTrue("Should contain ERROR", statuses.contains(AudioProcessingStatus.ERROR))
    }

    @Test
    fun `AudioProcessingError enum should have all expected values`() {
        // Given & When
        val errors = AudioProcessingError.values()
        
        // Then
        assertTrue("Should contain INITIALIZATION_FAILED", 
            errors.contains(AudioProcessingError.INITIALIZATION_FAILED))
        assertTrue("Should contain AUDIO_SOURCE_UNAVAILABLE", 
            errors.contains(AudioProcessingError.AUDIO_SOURCE_UNAVAILABLE))
        assertTrue("Should contain PERMISSION_DENIED", 
            errors.contains(AudioProcessingError.PERMISSION_DENIED))
        assertTrue("Should contain INSUFFICIENT_STORAGE", 
            errors.contains(AudioProcessingError.INSUFFICIENT_STORAGE))
        assertTrue("Should contain ENCODING_FAILED", 
            errors.contains(AudioProcessingError.ENCODING_FAILED))
        assertTrue("Should contain FILE_CREATION_FAILED", 
            errors.contains(AudioProcessingError.FILE_CREATION_FAILED))
        assertTrue("Should contain HARDWARE_ERROR", 
            errors.contains(AudioProcessingError.HARDWARE_ERROR))
        assertTrue("Should contain UNKNOWN_ERROR", 
            errors.contains(AudioProcessingError.UNKNOWN_ERROR))
    }

    @Test
    fun `AudioProcessingResult Success should contain correct data`() {
        // Given
        val testFile = File("test.m4a")
        val duration = 30000L
        val fileSize = 1024L
        val audioQuality = AudioQuality.STANDARD
        
        // When
        val result = AudioProcessingResult.Success(
            outputFile = testFile,
            duration = duration,
            fileSize = fileSize,
            audioQuality = audioQuality
        )
        
        // Then
        assertEquals("Output file should match", testFile, result.outputFile)
        assertEquals("Duration should match", duration, result.duration)
        assertEquals("File size should match", fileSize, result.fileSize)
        assertEquals("Audio quality should match", audioQuality, result.audioQuality)
    }

    @Test
    fun `AudioProcessingResult Error should contain correct data`() {
        // Given
        val error = AudioProcessingError.ENCODING_FAILED
        val message = "Test error message"
        
        // When
        val result = AudioProcessingResult.Error(
            error = error,
            message = message
        )
        
        // Then
        assertEquals("Error should match", error, result.error)
        assertEquals("Message should match", message, result.message)
    }

    @Test
    fun `AudioProcessingResult should be sealed class with correct inheritance`() {
        // Given
        val successResult = AudioProcessingResult.Success(
            File("test.m4a"), 1000L, 512L, AudioQuality.STANDARD
        )
        val errorResult = AudioProcessingResult.Error(
            AudioProcessingError.UNKNOWN_ERROR, "Test error"
        )
        
        // When & Then
        assertTrue("Success should be instance of AudioProcessingResult", 
            successResult is AudioProcessingResult)
        assertTrue("Error should be instance of AudioProcessingResult", 
            errorResult is AudioProcessingResult)
        
        // Test pattern matching
        when (successResult) {
            is AudioProcessingResult.Success -> {
                assertNotNull("Success result should have output file", successResult.outputFile)
            }
            is AudioProcessingResult.Error -> {
                fail("Should not be error result")
            }
        }
        
        when (errorResult) {
            is AudioProcessingResult.Success -> {
                fail("Should not be success result")
            }
            is AudioProcessingResult.Error -> {
                assertNotNull("Error result should have error", errorResult.error)
                assertNotNull("Error result should have message", errorResult.message)
            }
        }
    }

    @Test
    fun `AudioProcessingResult Success should handle different audio qualities`() {
        // Given
        val testFile = File("test.m4a")
        val duration = 15000L
        val fileSize = 2048L
        
        // When & Then
        for (quality in AudioQuality.values()) {
            val result = AudioProcessingResult.Success(
                outputFile = testFile,
                duration = duration,
                fileSize = fileSize,
                audioQuality = quality
            )
            
            assertEquals("Audio quality should be preserved for ${quality.displayName}", 
                quality, result.audioQuality)
        }
    }

    @Test
    fun `AudioProcessingResult Error should handle different error types`() {
        // Given
        val testMessage = "Test error message"
        
        // When & Then
        for (errorType in AudioProcessingError.values()) {
            val result = AudioProcessingResult.Error(
                error = errorType,
                message = testMessage
            )
            
            assertEquals("Error type should be preserved for $errorType", 
                errorType, result.error)
            assertEquals("Message should be preserved for $errorType", 
                testMessage, result.message)
        }
    }

    @Test
    fun `AudioProcessingStatus should support equality comparison`() {
        // Given
        val status1 = AudioProcessingStatus.CAPTURING
        val status2 = AudioProcessingStatus.CAPTURING
        val status3 = AudioProcessingStatus.IDLE
        
        // When & Then
        assertEquals("Same statuses should be equal", status1, status2)
        assertNotEquals("Different statuses should not be equal", status1, status3)
    }

    @Test
    fun `AudioProcessingError should support equality comparison`() {
        // Given
        val error1 = AudioProcessingError.ENCODING_FAILED
        val error2 = AudioProcessingError.ENCODING_FAILED
        val error3 = AudioProcessingError.PERMISSION_DENIED
        
        // When & Then
        assertEquals("Same errors should be equal", error1, error2)
        assertNotEquals("Different errors should not be equal", error1, error3)
    }

    @Test
    fun `AudioProcessingResult should support equality comparison`() {
        // Given
        val file1 = File("test1.m4a")
        val file2 = File("test2.m4a")
        
        val success1 = AudioProcessingResult.Success(file1, 1000L, 512L, AudioQuality.STANDARD)
        val success2 = AudioProcessingResult.Success(file1, 1000L, 512L, AudioQuality.STANDARD)
        val success3 = AudioProcessingResult.Success(file2, 1000L, 512L, AudioQuality.STANDARD)
        
        val error1 = AudioProcessingResult.Error(AudioProcessingError.UNKNOWN_ERROR, "Test")
        val error2 = AudioProcessingResult.Error(AudioProcessingError.UNKNOWN_ERROR, "Test")
        val error3 = AudioProcessingResult.Error(AudioProcessingError.ENCODING_FAILED, "Test")
        
        // When & Then
        assertEquals("Same success results should be equal", success1, success2)
        assertNotEquals("Different success results should not be equal", success1, success3)
        assertEquals("Same error results should be equal", error1, error2)
        assertNotEquals("Different error results should not be equal", error1, error3)
        assertNotEquals("Success and error results should not be equal", success1, error1)
    }
}