package com.callrecorder.pixel.audio

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Audio enhancement utility class for improving call recording quality.
 * Provides real-time gain adjustment, normalization, and noise reduction.
 */
class AudioEnhancer {

    companion object {
        private const val TAG = "AudioEnhancer"
        private const val SAMPLE_RATE_44100 = 44100
        private const val SAMPLE_RATE_48000 = 48000
        private const val BYTES_PER_SAMPLE = 2 // 16-bit audio
        private const val MAX_16BIT_VALUE = 32767.0
        private const val MIN_16BIT_VALUE = -32768.0
        
        // Noise reduction parameters
        private const val NOISE_GATE_THRESHOLD = 0.01 // 1% of max amplitude
        private const val NOISE_REDUCTION_FACTOR = 0.3
        
        // Dynamic range compression parameters
        private const val COMPRESSION_THRESHOLD = 0.7 // 70% of max amplitude
        private const val COMPRESSION_RATIO = 4.0 // 4:1 compression
        private const val ATTACK_TIME_MS = 5.0
        private const val RELEASE_TIME_MS = 50.0
    }

    /**
     * Applies comprehensive audio enhancement to a recorded file
     */
    suspend fun enhanceAudio(
        inputFile: File,
        outputFile: File,
        enableNoiseReduction: Boolean = true,
        enableNormalization: Boolean = true,
        enableCompression: Boolean = true,
        targetGainDb: Float = 0.0f
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting audio enhancement for: ${inputFile.name}")
            
            if (!inputFile.exists()) {
                Log.e(TAG, "Input file does not exist")
                return@withContext false
            }
            
            // Read audio data
            val audioData = readAudioData(inputFile)
            if (audioData.isEmpty()) {
                Log.e(TAG, "Failed to read audio data")
                return@withContext false
            }
            
            var processedData = audioData.copyOf()
            
            // Apply noise reduction
            if (enableNoiseReduction) {
                processedData = applyNoiseReduction(processedData)
                Log.d(TAG, "Noise reduction applied")
            }
            
            // Apply dynamic range compression
            if (enableCompression) {
                processedData = applyDynamicRangeCompression(processedData)
                Log.d(TAG, "Dynamic range compression applied")
            }
            
            // Apply gain adjustment
            if (targetGainDb != 0.0f) {
                processedData = applyGainAdjustment(processedData, targetGainDb)
                Log.d(TAG, "Gain adjustment applied: ${targetGainDb}dB")
            }
            
            // Apply normalization
            if (enableNormalization) {
                processedData = normalizeAudioData(processedData)
                Log.d(TAG, "Audio normalization applied")
            }
            
            // Write enhanced audio data
            val success = writeAudioData(processedData, outputFile)
            
            if (success) {
                Log.d(TAG, "Audio enhancement completed successfully")
            } else {
                Log.e(TAG, "Failed to write enhanced audio data")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio enhancement", e)
            false
        }
    }

    /**
     * Applies real-time gain adjustment to audio samples
     */
    fun applyRealtimeGainAdjustment(
        audioSamples: ShortArray,
        gainDb: Float
    ): ShortArray {
        val gainLinear = 10.0.pow(gainDb / 20.0).toFloat()
        
        return audioSamples.map { sample ->
            val adjustedSample = (sample * gainLinear).toInt()
            adjustedSample.coerceIn(MIN_16BIT_VALUE.toInt(), MAX_16BIT_VALUE.toInt()).toShort()
        }.toShortArray()
    }

    /**
     * Calculates the RMS (Root Mean Square) level of audio samples
     */
    fun calculateRMSLevel(audioSamples: ShortArray): Float {
        if (audioSamples.isEmpty()) return 0.0f
        
        val sumOfSquares = audioSamples.map { (it * it).toDouble() }.sum()
        val rms = sqrt(sumOfSquares / audioSamples.size)
        
        return (rms / MAX_16BIT_VALUE).toFloat()
    }

    /**
     * Applies automatic gain control (AGC) to maintain consistent audio levels
     */
    fun applyAutomaticGainControl(
        audioSamples: ShortArray,
        targetLevel: Float = 0.5f,
        maxGainDb: Float = 20.0f
    ): ShortArray {
        val currentLevel = calculateRMSLevel(audioSamples)
        
        if (currentLevel <= 0.0f) return audioSamples
        
        val requiredGainLinear = targetLevel / currentLevel
        val requiredGainDb = 20.0f * log10(requiredGainLinear)
        
        // Limit the gain to prevent distortion
        val limitedGainDb = requiredGainDb.coerceIn(-maxGainDb, maxGainDb)
        
        return applyRealtimeGainAdjustment(audioSamples, limitedGainDb)
    }

    /**
     * Reads audio data from a file (assumes 16-bit PCM format)
     */
    private suspend fun readAudioData(file: File): ShortArray = withContext(Dispatchers.IO) {
        try {
            val fileSize = file.length().toInt()
            val buffer = ByteArray(fileSize)
            
            FileInputStream(file).use { inputStream ->
                inputStream.read(buffer)
            }
            
            // Convert bytes to 16-bit samples
            val samples = ShortArray(buffer.size / BYTES_PER_SAMPLE)
            val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
            
            for (i in samples.indices) {
                samples[i] = byteBuffer.short
            }
            
            Log.d(TAG, "Read ${samples.size} audio samples from file")
            samples
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading audio data", e)
            shortArrayOf()
        }
    }

    /**
     * Writes audio data to a file (16-bit PCM format)
     */
    private suspend fun writeAudioData(samples: ShortArray, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(samples.size * BYTES_PER_SAMPLE)
            val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
            
            for (sample in samples) {
                byteBuffer.putShort(sample)
            }
            
            FileOutputStream(file).use { outputStream ->
                outputStream.write(buffer)
            }
            
            Log.d(TAG, "Wrote ${samples.size} audio samples to file")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio data", e)
            false
        }
    }   
 /**
     * Applies noise reduction using a simple noise gate and spectral subtraction
     */
    private fun applyNoiseReduction(samples: ShortArray): ShortArray {
        val processedSamples = samples.copyOf()
        val threshold = (MAX_16BIT_VALUE * NOISE_GATE_THRESHOLD).toInt()
        
        for (i in processedSamples.indices) {
            val sample = processedSamples[i].toInt()
            val amplitude = abs(sample)
            
            if (amplitude < threshold) {
                // Apply noise gate - reduce low-level signals
                processedSamples[i] = (sample * NOISE_REDUCTION_FACTOR).toInt().toShort()
            }
        }
        
        return processedSamples
    }

    /**
     * Applies dynamic range compression to reduce the difference between loud and quiet parts
     */
    private fun applyDynamicRangeCompression(samples: ShortArray): ShortArray {
        val processedSamples = samples.copyOf()
        val threshold = (MAX_16BIT_VALUE * COMPRESSION_THRESHOLD).toInt()
        
        for (i in processedSamples.indices) {
            val sample = processedSamples[i].toInt()
            val amplitude = abs(sample)
            
            if (amplitude > threshold) {
                // Apply compression above threshold
                val excessAmplitude = amplitude - threshold
                val compressedExcess = excessAmplitude / COMPRESSION_RATIO
                val compressedAmplitude = threshold + compressedExcess
                
                val sign = if (sample >= 0) 1 else -1
                processedSamples[i] = (compressedAmplitude * sign).toInt()
                    .coerceIn(MIN_16BIT_VALUE.toInt(), MAX_16BIT_VALUE.toInt()).toShort()
            }
        }
        
        return processedSamples
    }

    /**
     * Applies gain adjustment to audio samples
     */
    private fun applyGainAdjustment(samples: ShortArray, gainDb: Float): ShortArray {
        val gainLinear = 10.0.pow(gainDb / 20.0).toFloat()
        
        return samples.map { sample ->
            val adjustedSample = (sample * gainLinear).toInt()
            adjustedSample.coerceIn(MIN_16BIT_VALUE.toInt(), MAX_16BIT_VALUE.toInt()).toShort()
        }.toShortArray()
    }

    /**
     * Normalizes audio data to use the full dynamic range
     */
    private fun normalizeAudioData(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return samples
        
        // Find the maximum absolute amplitude
        val maxAmplitude = samples.maxOfOrNull { abs(it.toInt()) } ?: 0
        
        if (maxAmplitude == 0) return samples
        
        // Calculate normalization factor
        val normalizationFactor = MAX_16BIT_VALUE / maxAmplitude
        
        // Apply normalization
        return samples.map { sample ->
            (sample * normalizationFactor).toInt()
                .coerceIn(MIN_16BIT_VALUE.toInt(), MAX_16BIT_VALUE.toInt()).toShort()
        }.toShortArray()
    }

    /**
     * Applies a simple low-pass filter to reduce high-frequency noise
     */
    fun applyLowPassFilter(
        samples: ShortArray,
        cutoffFrequency: Float,
        sampleRate: Int = SAMPLE_RATE_44100
    ): ShortArray {
        if (samples.isEmpty()) return samples
        
        // Simple first-order low-pass filter
        val rc = 1.0f / (2.0f * PI.toFloat() * cutoffFrequency)
        val dt = 1.0f / sampleRate
        val alpha = dt / (rc + dt)
        
        val filteredSamples = ShortArray(samples.size)
        filteredSamples[0] = samples[0]
        
        for (i in 1 until samples.size) {
            val filtered = alpha * samples[i] + (1 - alpha) * filteredSamples[i - 1]
            filteredSamples[i] = filtered.toInt()
                .coerceIn(MIN_16BIT_VALUE.toInt(), MAX_16BIT_VALUE.toInt()).toShort()
        }
        
        return filteredSamples
    }

    /**
     * Applies a simple high-pass filter to reduce low-frequency noise
     */
    fun applyHighPassFilter(
        samples: ShortArray,
        cutoffFrequency: Float,
        sampleRate: Int = SAMPLE_RATE_44100
    ): ShortArray {
        if (samples.isEmpty()) return samples
        
        // Simple first-order high-pass filter
        val rc = 1.0f / (2.0f * PI.toFloat() * cutoffFrequency)
        val dt = 1.0f / sampleRate
        val alpha = rc / (rc + dt)
        
        val filteredSamples = ShortArray(samples.size)
        filteredSamples[0] = samples[0]
        
        for (i in 1 until samples.size) {
            val filtered = alpha * (filteredSamples[i - 1] + samples[i] - samples[i - 1])
            filteredSamples[i] = filtered.toInt()
                .coerceIn(MIN_16BIT_VALUE.toInt(), MAX_16BIT_VALUE.toInt()).toShort()
        }
        
        return filteredSamples
    }

    /**
     * Detects and removes silence from audio samples
     */
    fun removeSilence(
        samples: ShortArray,
        silenceThreshold: Float = 0.02f,
        minSilenceDurationMs: Int = 500,
        sampleRate: Int = SAMPLE_RATE_44100
    ): ShortArray {
        if (samples.isEmpty()) return samples
        
        val threshold = (MAX_16BIT_VALUE * silenceThreshold).toInt()
        val minSilenceSamples = (minSilenceDurationMs * sampleRate) / 1000
        
        val result = mutableListOf<Short>()
        var silenceCount = 0
        
        for (sample in samples) {
            val amplitude = abs(sample.toInt())
            
            if (amplitude < threshold) {
                silenceCount++
                if (silenceCount < minSilenceSamples) {
                    result.add(sample)
                }
                // Skip samples that are part of long silence
            } else {
                silenceCount = 0
                result.add(sample)
            }
        }
        
        return result.toShortArray()
    }
}