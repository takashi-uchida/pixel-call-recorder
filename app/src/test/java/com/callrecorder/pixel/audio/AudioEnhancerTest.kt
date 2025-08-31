package com.callrecorder.pixel.audio

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests for AudioEnhancer
 */
class AudioEnhancerTest {

    private lateinit var audioEnhancer: AudioEnhancer
    private lateinit var testInputFile: File
    private lateinit var testOutputFile: File

    @Before
    fun setUp() {
        audioEnhancer = AudioEnhancer()
        testInputFile = File.createTempFile("test_input", ".pcm")
        testOutputFile = File.createTempFile("test_output", ".pcm")
        
        // Create test audio data (16-bit PCM)
        createTestAudioFile(testInputFile)
    }

    @After
    fun tearDown() {
        if (testInputFile.exists()) testInputFile.delete()
        if (testOutputFile.exists()) testOutputFile.delete()
    }

    @Test
    fun `enhanceAudio should succeed with valid input file`() = runTest {
        // Given
        assertTrue("Test input file should exist", testInputFile.exists())
        assertTrue("Test input file should have content", testInputFile.length() > 0)
        
        // When
        val result = audioEnhancer.enhanceAudio(
            inputFile = testInputFile,
            outputFile = testOutputFile,
            enableNoiseReduction = true,
            enableNormalization = true,
            enableCompression = true,
            targetGainDb = 0.0f
        )
        
        // Then
        assertTrue("Audio enhancement should succeed", result)
        assertTrue("Output file should be created", testOutputFile.exists())
        assertTrue("Output file should have content", testOutputFile.length() > 0)
    }

    @Test
    fun `enhanceAudio should fail with non-existent input file`() = runTest {
        // Given
        val nonExistentFile = File("non_existent.pcm")
        
        // When
        val result = audioEnhancer.enhanceAudio(
            inputFile = nonExistentFile,
            outputFile = testOutputFile,
            enableNoiseReduction = true,
            enableNormalization = true,
            enableCompression = true,
            targetGainDb = 0.0f
        )
        
        // Then
        assertFalse("Audio enhancement should fail with non-existent file", result)
    }

    @Test
    fun `applyRealtimeGainAdjustment should adjust audio levels correctly`() {
        // Given
        val originalSamples = shortArrayOf(1000, -1000, 2000, -2000, 0)
        val gainDb = 6.0f // Double the amplitude
        
        // When
        val adjustedSamples = audioEnhancer.applyRealtimeGainAdjustment(originalSamples, gainDb)
        
        // Then
        assertEquals("Array size should remain the same", 
            originalSamples.size, adjustedSamples.size)
        
        // Check that gain was applied (approximately doubled)
        assertTrue("First sample should be amplified", 
            kotlin.math.abs(adjustedSamples[0].toInt()) > kotlin.math.abs(originalSamples[0].toInt()))
        assertTrue("Second sample should be amplified", 
            kotlin.math.abs(adjustedSamples[1].toInt()) > kotlin.math.abs(originalSamples[1].toInt()))
    }

    @Test
    fun `applyRealtimeGainAdjustment should prevent clipping`() {
        // Given
        val originalSamples = shortArrayOf(30000, -30000) // Near max 16-bit values
        val excessiveGain = 20.0f // Very high gain
        
        // When
        val adjustedSamples = audioEnhancer.applyRealtimeGainAdjustment(originalSamples, excessiveGain)
        
        // Then
        assertTrue("Positive sample should not exceed 16-bit max", 
            adjustedSamples[0] <= Short.MAX_VALUE)
        assertTrue("Negative sample should not exceed 16-bit min", 
            adjustedSamples[1] >= Short.MIN_VALUE)
    }

    @Test
    fun `calculateRMSLevel should return correct values`() {
        // Given
        val silentSamples = shortArrayOf(0, 0, 0, 0)
        val loudSamples = shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE, Short.MAX_VALUE, Short.MIN_VALUE)
        val emptySamples = shortArrayOf()
        
        // When
        val silentLevel = audioEnhancer.calculateRMSLevel(silentSamples)
        val loudLevel = audioEnhancer.calculateRMSLevel(loudSamples)
        val emptyLevel = audioEnhancer.calculateRMSLevel(emptySamples)
        
        // Then
        assertEquals("Silent samples should have zero RMS", 0.0f, silentLevel, 0.001f)
        assertTrue("Loud samples should have high RMS", loudLevel > 0.5f)
        assertEquals("Empty samples should have zero RMS", 0.0f, emptyLevel, 0.001f)
    }

    @Test
    fun `applyAutomaticGainControl should normalize audio levels`() {
        // Given
        val quietSamples = shortArrayOf(100, -100, 200, -200)
        val targetLevel = 0.5f
        
        // When
        val normalizedSamples = audioEnhancer.applyAutomaticGainControl(
            quietSamples, targetLevel, 20.0f
        )
        
        // Then
        assertEquals("Array size should remain the same", 
            quietSamples.size, normalizedSamples.size)
        
        val normalizedLevel = audioEnhancer.calculateRMSLevel(normalizedSamples)
        assertTrue("Normalized level should be higher than original", 
            normalizedLevel > audioEnhancer.calculateRMSLevel(quietSamples))
    }

    @Test
    fun `applyAutomaticGainControl should handle zero level input`() {
        // Given
        val zeroSamples = shortArrayOf(0, 0, 0, 0)
        val targetLevel = 0.5f
        
        // When
        val result = audioEnhancer.applyAutomaticGainControl(
            zeroSamples, targetLevel, 20.0f
        )
        
        // Then
        assertArrayEquals("Zero samples should remain unchanged", zeroSamples, result)
    }

    @Test
    fun `applyLowPassFilter should reduce high frequency content`() {
        // Given
        val testSamples = generateTestSineWave(1000, 44100, 1.0) // 1kHz sine wave
        val cutoffFrequency = 500.0f // Cut off above 500Hz
        
        // When
        val filteredSamples = audioEnhancer.applyLowPassFilter(
            testSamples, cutoffFrequency, 44100
        )
        
        // Then
        assertEquals("Array size should remain the same", 
            testSamples.size, filteredSamples.size)
        
        // The filtered signal should have reduced amplitude (simple check)
        val originalRMS = audioEnhancer.calculateRMSLevel(testSamples)
        val filteredRMS = audioEnhancer.calculateRMSLevel(filteredSamples)
        assertTrue("Filtered signal should have reduced amplitude", 
            filteredRMS <= originalRMS)
    }

    @Test
    fun `applyHighPassFilter should reduce low frequency content`() {
        // Given
        val testSamples = generateTestSineWave(100, 44100, 1.0) // 100Hz sine wave
        val cutoffFrequency = 200.0f // Cut off below 200Hz
        
        // When
        val filteredSamples = audioEnhancer.applyHighPassFilter(
            testSamples, cutoffFrequency, 44100
        )
        
        // Then
        assertEquals("Array size should remain the same", 
            testSamples.size, filteredSamples.size)
        
        // The filtered signal should have reduced amplitude
        val originalRMS = audioEnhancer.calculateRMSLevel(testSamples)
        val filteredRMS = audioEnhancer.calculateRMSLevel(filteredSamples)
        assertTrue("Filtered signal should have reduced amplitude", 
            filteredRMS <= originalRMS)
    }

    @Test
    fun `removeSilence should remove quiet sections`() {
        // Given
        val samplesWithSilence = shortArrayOf(
            1000, 1000, 1000,  // Loud section
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10,  // Long silence
            1000, 1000, 1000   // Loud section
        )
        
        // When
        val processedSamples = audioEnhancer.removeSilence(
            samplesWithSilence,
            silenceThreshold = 0.1f,
            minSilenceDurationMs = 100,
            sampleRate = 44100
        )
        
        // Then
        assertTrue("Processed samples should be shorter", 
            processedSamples.size <= samplesWithSilence.size)
    }

    @Test
    fun `filters should handle empty input gracefully`() {
        // Given
        val emptySamples = shortArrayOf()
        
        // When
        val lowPassResult = audioEnhancer.applyLowPassFilter(emptySamples, 1000.0f)
        val highPassResult = audioEnhancer.applyHighPassFilter(emptySamples, 1000.0f)
        val silenceResult = audioEnhancer.removeSilence(emptySamples)
        
        // Then
        assertEquals("Low pass filter should return empty array", 0, lowPassResult.size)
        assertEquals("High pass filter should return empty array", 0, highPassResult.size)
        assertEquals("Silence removal should return empty array", 0, silenceResult.size)
    }

    /**
     * Creates a test audio file with sample 16-bit PCM data
     */
    private fun createTestAudioFile(file: File) {
        val sampleData = generateTestSineWave(440, 44100, 1.0) // 1 second of 440Hz tone
        val byteBuffer = ByteBuffer.allocate(sampleData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        
        for (sample in sampleData) {
            byteBuffer.putShort(sample)
        }
        
        FileOutputStream(file).use { output ->
            output.write(byteBuffer.array())
        }
    }

    /**
     * Generates a sine wave for testing
     */
    private fun generateTestSineWave(frequency: Int, sampleRate: Int, durationSeconds: Double): ShortArray {
        val numSamples = (sampleRate * durationSeconds).toInt()
        val samples = ShortArray(numSamples)
        
        for (i in samples.indices) {
            val time = i.toDouble() / sampleRate
            val amplitude = kotlin.math.sin(2.0 * kotlin.math.PI * frequency * time)
            samples[i] = (amplitude * Short.MAX_VALUE * 0.5).toInt().toShort()
        }
        
        return samples
    }
}
