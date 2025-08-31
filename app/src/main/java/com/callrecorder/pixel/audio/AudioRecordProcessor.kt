package com.callrecorder.pixel.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.callrecorder.pixel.data.model.AudioQuality
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10

/**
 * AudioRecord-based implementation for real-time audio processing.
 * Provides more granular control over audio capture and real-time enhancement.
 */
class AudioRecordProcessor(
    private val context: Context
) : AudioProcessor {

    companion object {
        private const val TAG = "AudioRecordProcessor"
        // Not a compile-time constant in Kotlin; keep as runtime val
        private val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_CALL
        private const val BUFFER_SIZE_MULTIPLIER = 4
        private const val MAX_16BIT_VALUE = 32767.0
    }

    private var audioRecord: AudioRecord? = null
    private var currentAudioQuality: AudioQuality = AudioQuality.STANDARD
    private var currentOutputFile: File? = null
    private var recordingStartTime: Long = 0
    private var currentStatus: AudioProcessingStatus = AudioProcessingStatus.IDLE
    private var isInitialized: Boolean = false
    private val audioEnhancer: AudioEnhancer = AudioEnhancer()
    private var currentGainLevel: Float = 0.0f
    private var recordingJob: Job? = null
    private var bufferSize: Int = 0
    private var fileOutputStream: FileOutputStream? = null

    override suspend fun initializeAudioCapture(audioQuality: AudioQuality): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing AudioRecord with quality: ${audioQuality.displayName}")
            currentStatus = AudioProcessingStatus.INITIALIZING
            
            currentAudioQuality = audioQuality
            
            // Calculate buffer size
            val channelConfig = if (audioQuality.channels == 1) {
                AudioFormat.CHANNEL_IN_MONO
            } else {
                AudioFormat.CHANNEL_IN_STEREO
            }
            
            val minBufferSize = AudioRecord.getMinBufferSize(
                audioQuality.sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
                Log.e(TAG, "Invalid audio parameters")
                currentStatus = AudioProcessingStatus.ERROR
                return@withContext false
            }
            
            bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER
            
            // Create AudioRecord instance
            audioRecord = AudioRecord(
                AUDIO_SOURCE,
                audioQuality.sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                currentStatus = AudioProcessingStatus.ERROR
                return@withContext false
            }
            
            isInitialized = true
            currentStatus = AudioProcessingStatus.IDLE
            Log.d(TAG, "AudioRecord initialized successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord", e)
            currentStatus = AudioProcessingStatus.ERROR
            false
        }
    }

    override suspend fun startCapture(outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized || audioRecord == null) {
                Log.e(TAG, "AudioRecord not initialized")
                return@withContext false
            }
            
            if (currentStatus == AudioProcessingStatus.CAPTURING) {
                Log.w(TAG, "Already capturing audio")
                return@withContext false
            }
            
            Log.d(TAG, "Starting AudioRecord capture to: ${outputFile.absolutePath}")
            currentStatus = AudioProcessingStatus.CAPTURING
            currentOutputFile = outputFile
            
            // Ensure output directory exists
            outputFile.parentFile?.mkdirs()
            
            // Create output stream
            fileOutputStream = FileOutputStream(outputFile)
            
            // Start recording
            audioRecord?.startRecording()
            recordingStartTime = System.currentTimeMillis()
            
            // Start recording coroutine
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                recordAudioData()
            }
            
            Log.d(TAG, "AudioRecord capture started successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord capture", e)
            currentStatus = AudioProcessingStatus.ERROR
            cleanup()
            false
        }
    }

    override suspend fun stopCapture(): AudioProcessingResult = withContext(Dispatchers.IO) {
        try {
            if (currentStatus != AudioProcessingStatus.CAPTURING) {
                Log.w(TAG, "Not currently capturing audio")
                return@withContext AudioProcessingResult.Error(
                    AudioProcessingError.UNKNOWN_ERROR,
                    "Not currently capturing audio"
                )
            }
            
            Log.d(TAG, "Stopping AudioRecord capture")
            currentStatus = AudioProcessingStatus.FINALIZING
            
            val duration = System.currentTimeMillis() - recordingStartTime
            val outputFile = currentOutputFile
            
            // Stop recording
            recordingJob?.cancel()
            audioRecord?.stop()
            fileOutputStream?.close()
            
            if (outputFile != null && outputFile.exists()) {
                val fileSize = outputFile.length()
                currentStatus = AudioProcessingStatus.IDLE
                
                Log.d(TAG, "AudioRecord capture completed. Duration: ${duration}ms, Size: ${fileSize} bytes")
                
                AudioProcessingResult.Success(
                    outputFile = outputFile,
                    duration = duration,
                    fileSize = fileSize,
                    audioQuality = currentAudioQuality
                )
            } else {
                Log.e(TAG, "Output file not found after recording")
                currentStatus = AudioProcessingStatus.ERROR
                AudioProcessingResult.Error(
                    AudioProcessingError.FILE_CREATION_FAILED,
                    "Output file not created"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop AudioRecord capture", e)
            currentStatus = AudioProcessingStatus.ERROR
            cleanup()
            AudioProcessingResult.Error(
                AudioProcessingError.UNKNOWN_ERROR,
                "Failed to stop recording: ${e.message}"
            )
        }
    }

    override suspend fun pauseCapture(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (currentStatus != AudioProcessingStatus.CAPTURING) {
                return@withContext false
            }
            
            recordingJob?.cancel()
            audioRecord?.stop()
            currentStatus = AudioProcessingStatus.PAUSED
            Log.d(TAG, "AudioRecord capture paused")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause AudioRecord capture", e)
            false
        }
    }

    override suspend fun resumeCapture(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (currentStatus != AudioProcessingStatus.PAUSED) {
                return@withContext false
            }
            
            audioRecord?.startRecording()
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                recordAudioData()
            }
            currentStatus = AudioProcessingStatus.CAPTURING
            Log.d(TAG, "AudioRecord capture resumed")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume AudioRecord capture", e)
            false
        }
    }
    override suspend fun applyAudioEnhancement(inputFile: File, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Applying audio enhancement to: ${inputFile.name}")
            currentStatus = AudioProcessingStatus.ENHANCING
            
            if (!inputFile.exists()) {
                Log.e(TAG, "Input file does not exist")
                currentStatus = AudioProcessingStatus.ERROR
                return@withContext false
            }
            
            val enhanced = audioEnhancer.enhanceAudio(
                inputFile = inputFile,
                outputFile = outputFile,
                enableNoiseReduction = true,
                enableNormalization = true,
                enableCompression = true,
                targetGainDb = currentGainLevel
            )
            
            currentStatus = AudioProcessingStatus.IDLE
            Log.d(TAG, "Audio enhancement completed: $enhanced")
            enhanced
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply audio enhancement", e)
            currentStatus = AudioProcessingStatus.ERROR
            false
        }
    }

    override suspend fun normalizeAudio(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Normalizing audio for file: ${file.name}")
            currentStatus = AudioProcessingStatus.PROCESSING
            
            if (!file.exists()) {
                Log.e(TAG, "File does not exist for normalization")
                currentStatus = AudioProcessingStatus.ERROR
                return@withContext false
            }
            
            // Use AudioEnhancer for normalization
            val tempFile = File(file.parent, "${file.nameWithoutExtension}_temp.${file.extension}")
            val normalized = audioEnhancer.enhanceAudio(
                inputFile = file,
                outputFile = tempFile,
                enableNoiseReduction = false,
                enableNormalization = true,
                enableCompression = false,
                targetGainDb = 0.0f
            )
            
            if (normalized) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
                currentStatus = AudioProcessingStatus.IDLE
                Log.d(TAG, "Audio normalization completed")
                true
            } else {
                tempFile.delete()
                currentStatus = AudioProcessingStatus.ERROR
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to normalize audio", e)
            currentStatus = AudioProcessingStatus.ERROR
            false
        }
    }

    override suspend fun applyRealtimeGain(gainLevel: Float): Boolean = withContext(Dispatchers.IO) {
        try {
            val clampedGain = gainLevel.coerceIn(-20.0f, 20.0f)
            currentGainLevel = clampedGain
            
            Log.d(TAG, "Realtime gain set to: $clampedGain dB")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply realtime gain", e)
            false
        }
    }

    override fun getCurrentAudioLevel(): Float? {
        return try {
            if (currentStatus == AudioProcessingStatus.CAPTURING && audioRecord != null) {
                // This would require reading current audio data to calculate level
                // For now, return a placeholder value
                null
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current audio level", e)
            null
        }
    }

    override fun getCurrentDuration(): Long {
        return if (currentStatus == AudioProcessingStatus.CAPTURING && recordingStartTime > 0) {
            System.currentTimeMillis() - recordingStartTime
        } else {
            0L
        }
    }

    override fun isCapturing(): Boolean {
        return currentStatus == AudioProcessingStatus.CAPTURING
    }

    override fun getProcessingStatus(): AudioProcessingStatus {
        return currentStatus
    }

    override suspend fun release() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Releasing AudioRecord resources")
                if (currentStatus == AudioProcessingStatus.CAPTURING) {
                    stopCapture()
                }
                cleanup()
                currentStatus = AudioProcessingStatus.IDLE
                isInitialized = false
                Log.d(TAG, "AudioRecord resources released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioRecord resources", e)
            }
        }
    }

    /**
     * Records audio data in a coroutine
     */
    private suspend fun recordAudioData() = withContext(Dispatchers.IO) {
        try {
            val buffer = ShortArray(bufferSize / 2) // 16-bit samples
            val byteBuffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)
            
            while (currentStatus == AudioProcessingStatus.CAPTURING && !currentCoroutineContext().job.isCancelled) {
                val samplesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (samplesRead > 0) {
                    // Apply real-time gain adjustment if needed
                    val processedBuffer = if (currentGainLevel != 0.0f) {
                        audioEnhancer.applyRealtimeGainAdjustment(buffer, currentGainLevel)
                    } else {
                        buffer
                    }
                    
                    // Convert to bytes and write to file
                    byteBuffer.clear()
                    for (sample in processedBuffer) {
                        byteBuffer.putShort(sample)
                    }
                    
                    fileOutputStream?.write(byteBuffer.array(), 0, samplesRead * 2)
                }
                
                // Small delay to prevent excessive CPU usage
                delay(1)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio recording", e)
            currentStatus = AudioProcessingStatus.ERROR
        }
    }

    /**
     * Cleans up resources
     */
    private fun cleanup() {
        try {
            recordingJob?.cancel()
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
            fileOutputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        } finally {
            audioRecord = null
            fileOutputStream = null
            currentOutputFile = null
            recordingStartTime = 0
            recordingJob = null
        }
    }
}
