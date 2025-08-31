package com.callrecorder.pixel.service

import com.callrecorder.pixel.data.model.CallInfo
import com.callrecorder.pixel.data.model.Recording
import com.callrecorder.pixel.error.RecordingError

/**
 * Interface for the call recording service.
 * Defines the contract for managing call recording operations.
 */
interface CallRecordingService {

    /**
     * Starts recording for the specified call
     * @param callInfo Information about the call to record
     * @return true if recording started successfully, false otherwise
     */
    suspend fun startRecording(callInfo: CallInfo): Boolean

    /**
     * Stops the current recording
     * @return RecordingResult containing the recording details or error information
     */
    suspend fun stopRecording(): RecordingResult

    /**
     * Checks if recording is currently active
     * @return true if recording is in progress, false otherwise
     */
    fun isRecording(): Boolean

    /**
     * Gets the current recording status
     * @return RecordingStatus indicating the current state
     */
    fun getRecordingStatus(): RecordingStatus

    /**
     * Gets information about the current recording session
     * @return CallInfo if recording is active, null otherwise
     */
    fun getCurrentCallInfo(): CallInfo?

    /**
     * Gets the duration of the current recording in milliseconds
     * @return recording duration or 0 if not recording
     */
    fun getCurrentRecordingDuration(): Long

    /**
     * Pauses the current recording (if supported)
     * @return true if paused successfully, false otherwise
     */
    suspend fun pauseRecording(): Boolean

    /**
     * Resumes a paused recording
     * @return true if resumed successfully, false otherwise
     */
    suspend fun resumeRecording(): Boolean

    /**
     * Cancels the current recording without saving
     * @return true if cancelled successfully, false otherwise
     */
    suspend fun cancelRecording(): Boolean
}

/**
 * Enum representing different recording states
 */
enum class RecordingStatus {
    IDLE,           // Not recording
    INITIALIZING,   // Preparing to record
    RECORDING,      // Currently recording
    PAUSED,         // Recording paused
    STOPPING,       // Stopping recording
    PROCESSING,     // Post-processing audio
    ERROR           // Error state
}

/**
 * Sealed class representing the result of a recording operation
 */
sealed class RecordingResult {
    data class Success(val recording: Recording) : RecordingResult()
    data class Error(val error: RecordingError, val message: String) : RecordingResult()
    object Cancelled : RecordingResult()
}