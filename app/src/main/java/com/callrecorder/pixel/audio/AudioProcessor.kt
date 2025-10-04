package com.callrecorder.pixel.audio

import com.callrecorder.pixel.data.model.AudioQuality
import java.io.File

/**
 * Interface for audio processing operations.
 * Handles audio capture, enhancement, and encoding for call recordings.
 */
interface AudioProcessor {

    /**
     * Initializes the audio capture system
     * @param audioQuality The desired audio quality settings
     * @return true if initialization successful, false otherwise
     */
    suspend fun initializeAudioCapture(audioQuality: AudioQuality): Boolean

    /**
     * Starts audio capture to the specified output file
     * @param outputFile The file where audio will be saved
     * @return true if capture started successfully, false otherwise
     */
    suspend fun startCapture(outputFile: File): Boolean

    /**
     * Stops audio capture and finalizes the recording
     * @return AudioProcessingResult containing success/error information
     */
    suspend fun stopCapture(): AudioProcessingResult

    /**
     * Pauses audio capture (if supported)
     * @return true if paused successfully, false otherwise
     */
    suspend fun pauseCapture(): Boolean

    /**
     * Resumes audio capture from pause
     * @return true if resumed successfully, false otherwise
     */
    suspend fun resumeCapture(): Boolean

    /**
     * Applies audio enhancement to a recorded file
     * @param inputFile The original recording file
     * @param outputFile The enhanced output file
     * @return true if enhancement applied successfully, false otherwise
     */
    suspend fun applyAudioEnhancement(inputFile: File, outputFile: File): Boolean

    /**
     * Normalizes audio levels in a recording
     * @param file The file to normalize
     * @return true if normalization successful, false otherwise
     */
    suspend fun normalizeAudio(file: File): Boolean

    /**
     * Applies real-time gain adjustment during recording
     * @param gainLevel The gain level to apply (-20.0 to +20.0 dB)
     * @return true if gain applied successfully, false otherwise
     */
    suspend fun applyRealtimeGain(gainLevel: Float): Boolean

    /**
     * Gets the current audio input level (for VU meter display)
     * @return audio level in dB, or null if not available
     */
    fun getCurrentAudioLevel(): Float?

    /**
     * Gets the current recording duration in milliseconds
     * @return duration or 0 if not recording
     */
    fun getCurrentDuration(): Long

    /**
     * Checks if audio capture is currently active
     * @return true if capturing, false otherwise
     */
    fun isCapturing(): Boolean

    /**
     * Gets the current audio processing status
     * @return AudioProcessingStatus indicating current state
     */
    fun getProcessingStatus(): AudioProcessingStatus

    /**
     * Releases audio resources and cleans up
     */
    suspend fun release()
}

/**
 * Enum representing different audio processing states
 */
enum class AudioProcessingStatus {
    IDLE,           // Not processing
    INITIALIZING,   // Setting up audio capture
    CAPTURING,      // Currently capturing audio
    PAUSED,         // Capture paused
    PROCESSING,     // Post-processing audio
    ENHANCING,      // Applying audio enhancements
    FINALIZING,     // Finalizing the recording
    ERROR           // Error state
}

/**
 * Sealed class representing audio processing results
 */
sealed class AudioProcessingResult {
    data class Success(
        val outputFile: File,
        val duration: Long,
        val fileSize: Long,
        val audioQuality: AudioQuality
    ) : AudioProcessingResult()
    
    data class Error(
        val error: AudioProcessingError,
        val message: String
    ) : AudioProcessingResult()
}

/**
 * Enum representing different audio processing errors
 */
enum class AudioProcessingError {
    INITIALIZATION_FAILED,
    AUDIO_SOURCE_UNAVAILABLE,
    PERMISSION_DENIED,
    INSUFFICIENT_STORAGE,
    ENCODING_FAILED,
    FILE_CREATION_FAILED,
    HARDWARE_ERROR,
    UNKNOWN_ERROR
}