package com.callrecorder.pixel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.callrecorder.pixel.R
import com.callrecorder.pixel.audio.AudioProcessor
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.CallInfo
import com.callrecorder.pixel.data.model.Recording
import com.callrecorder.pixel.error.RecordingError
import com.callrecorder.pixel.error.ErrorHandler
import com.callrecorder.pixel.error.ErrorReporter
import com.callrecorder.pixel.error.ErrorRecovery
import com.callrecorder.pixel.error.SystemError
import com.callrecorder.pixel.error.StorageError
import com.callrecorder.pixel.storage.FileManager
import com.callrecorder.pixel.storage.RecordingMetadata
import com.callrecorder.pixel.ui.MainActivity
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground service implementation for call recording.
 * Handles call state monitoring, recording control, and audio processing.
 */
class CallRecordingServiceImpl @JvmOverloads constructor(
    private val injectedContext: Context? = null,
    private val injectedAudioProcessor: com.callrecorder.pixel.audio.MediaRecorderAudioProcessor? = null,
    private val injectedFileManager: com.callrecorder.pixel.storage.FileManager? = null,
    private val injectedRepository: com.callrecorder.pixel.data.repository.RecordingRepository? = null,
    private val injectedPermissionManager: com.callrecorder.pixel.permission.PermissionManager? = null
) : Service(), CallRecordingService {

    companion object {
        private const val TAG = "CallRecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "call_recording_channel"
        private const val CHANNEL_NAME = "通話録音サービス"
        
        // Service actions
        const val ACTION_START_RECORDING = "com.callrecorder.pixel.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.callrecorder.pixel.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.callrecorder.pixel.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.callrecorder.pixel.RESUME_RECORDING"
        
        // Intent extras
        const val EXTRA_CALL_INFO = "call_info"
    }

    // Service components
    private lateinit var audioProcessor: AudioProcessor
    private lateinit var fileManager: FileManager
    private var repository: com.callrecorder.pixel.data.repository.RecordingRepository? = null
    private var permissionManager: com.callrecorder.pixel.permission.PermissionManager? = null
    private lateinit var telecomManager: TelecomManager
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var errorHandler: ErrorHandler
    private lateinit var errorReporter: ErrorReporter
    private lateinit var errorRecovery: ErrorRecovery
    
    // Service state
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val isRecording = AtomicBoolean(false)
    private val currentStatus = AtomicReference(RecordingStatus.IDLE)
    private val currentCallInfo = AtomicReference<CallInfo?>(null)
    private val currentRecordingFile = AtomicReference<File?>(null)
    private val recordingStartTime = AtomicReference<Long?>(null)
    private val lastCallInfoById = mutableMapOf<String, CallInfo>()
    
    // Binder for local service binding
    private val binder = CallRecordingBinder()
    
    inner class CallRecordingBinder : Binder() {
        fun getService(): CallRecordingServiceImpl = this@CallRecordingServiceImpl
    }

    // Note: Android framework will use the no-arg overload; tests can inject via constructor

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        try {
            // Initialize system services
            telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Initialize dependencies (prefer injected when provided)
            val ctx = injectedContext ?: this
            errorHandler = ErrorHandler(ctx)
            errorReporter = ErrorReporter(ctx)
            
            injectedFileManager?.let { fileManager = it }
            injectedAudioProcessor?.let { audioProcessor = it }
            repository = injectedRepository
            permissionManager = injectedPermissionManager
            
            // Initialize error recovery with current fileManager if available
            if (this::fileManager.isInitialized) {
                errorRecovery = ErrorRecovery(ctx, fileManager)
            }
            
            // Create notification channel
            createNotificationChannel()
            
            // TODO: Initialize audio processor and file manager through dependency injection
            // For now, we'll assume they are injected or created elsewhere
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize service", e)
            // Report the error but don't crash the service
            if (::errorReporter.isInitialized) {
                errorReporter.reportCrash(e, "Service initialization failed")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val callInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_CALL_INFO, CallInfo::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_CALL_INFO)
                }
                callInfo?.let { 
                    serviceScope.launch {
                        startRecording(it)
                    }
                }
            }
            ACTION_STOP_RECORDING -> {
                serviceScope.launch {
                    stopRecording()
                }
            }
            ACTION_PAUSE_RECORDING -> {
                serviceScope.launch {
                    pauseRecording()
                }
            }
            ACTION_RESUME_RECORDING -> {
                serviceScope.launch {
                    resumeRecording()
                }
            }
        }
        
        return START_STICKY // Restart service if killed
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        
        // Stop any ongoing recording
        serviceScope.launch {
            if (isRecording.get()) {
                stopRecording()
            }
        }
        
        // Cancel all coroutines
        serviceScope.cancel()
        
        super.onDestroy()
    }

    // CallRecordingService interface implementation

    override suspend fun startRecording(callInfo: CallInfo): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting recording for call: ${callInfo.phoneNumber}")
        
        val startTime = System.currentTimeMillis()
        
        try {
            // Check if already recording
            if (isRecording.get()) {
                val error = RecordingError.RecordingInProgress
                errorReporter.reportError(error, "startRecording called while already recording")
                Log.w(TAG, "Recording already in progress")
                return@withContext false
            }
            
            // Check storage space before starting
            if (!errorRecovery.hasEnoughStorageSpace()) {
                val error = RecordingError.InsufficientStorage
                errorReporter.reportError(error, "Insufficient storage space for recording")
                
                // Attempt recovery by cleaning old recordings
                val recoveryResult = errorRecovery.attemptRecovery(error)
                if (recoveryResult.isError) {
                    errorHandler.handleError(error, showDialog = false)
                    currentStatus.set(RecordingStatus.ERROR)
                    return@withContext false
                }
            }
            
            // Update status
            currentStatus.set(RecordingStatus.INITIALIZING)
            currentCallInfo.set(callInfo)
            
            // Start foreground service
            startForeground(NOTIFICATION_ID, createRecordingNotification(callInfo))
            
            // Create recording file with error handling
            val recordingFile = try {
                fileManager.createRecordingFile(callInfo)
            } catch (e: Exception) {
                val error = RecordingError.FileCreationFailed
                errorReporter.reportError(error, "Failed to create recording file", 
                    mapOf("callInfo" to callInfo.toString()), e)
                
                // Attempt recovery
                val recoveryResult = errorRecovery.attemptRecovery(error)
                if (recoveryResult.isSuccess) {
                    // Retry file creation
                    try {
                        fileManager.createRecordingFile(callInfo)
                    } catch (retryException: Exception) {
                        errorHandler.handleError(error, showDialog = false)
                        currentStatus.set(RecordingStatus.ERROR)
                        return@withContext false
                    }
                } else {
                    errorHandler.handleError(error, showDialog = false)
                    currentStatus.set(RecordingStatus.ERROR)
                    return@withContext false
                }
            }
            
            if (recordingFile == null) {
                val error = RecordingError.FileCreationFailed
                errorReporter.reportError(error, "Recording file is null after creation")
                errorHandler.handleError(error, showDialog = false)
                currentStatus.set(RecordingStatus.ERROR)
                return@withContext false
            }
            
            currentRecordingFile.set(recordingFile)
            
            // Initialize audio processor with error handling
            val audioQuality = AudioQuality.STANDARD // TODO: Get from settings
            val audioInitialized = try {
                audioProcessor.initializeAudioCapture(audioQuality)
            } catch (e: Exception) {
                val error = RecordingError.AudioSourceNotAvailable
                errorReporter.reportError(error, "Audio processor initialization failed", 
                    mapOf("audioQuality" to audioQuality.toString()), e)
                
                // Attempt recovery
                val recoveryResult = errorRecovery.attemptRecovery(error)
                if (recoveryResult.isSuccess) {
                    // Retry initialization
                    try {
                        audioProcessor.initializeAudioCapture(audioQuality)
                    } catch (retryException: Exception) {
                        errorHandler.handleError(error, showDialog = false)
                        currentStatus.set(RecordingStatus.ERROR)
                        return@withContext false
                    }
                } else {
                    errorHandler.handleError(error, showDialog = false)
                    currentStatus.set(RecordingStatus.ERROR)
                    return@withContext false
                }
            }
            
            if (!audioInitialized) {
                val error = RecordingError.AudioSourceNotAvailable
                errorReporter.reportError(error, "Audio capture initialization returned false")
                errorHandler.handleError(error, showDialog = false)
                currentStatus.set(RecordingStatus.ERROR)
                return@withContext false
            }
            
            // Start audio capture with error handling
            val captureStarted = try {
                audioProcessor.startCapture(recordingFile)
            } catch (e: Exception) {
                val error = RecordingError.AudioProcessingFailed
                errorReporter.reportError(error, "Failed to start audio capture", 
                    mapOf("recordingFile" to recordingFile.absolutePath), e)
                errorHandler.handleError(error, showDialog = false)
                currentStatus.set(RecordingStatus.ERROR)
                return@withContext false
            }
            
            if (!captureStarted) {
                val error = RecordingError.AudioProcessingFailed
                errorReporter.reportError(error, "Audio capture start returned false")
                errorHandler.handleError(error, showDialog = false)
                currentStatus.set(RecordingStatus.ERROR)
                return@withContext false
            }
            
            // Update state
            isRecording.set(true)
            currentStatus.set(RecordingStatus.RECORDING)
            recordingStartTime.set(System.currentTimeMillis())
            
            // Update notification
            updateNotification(callInfo, RecordingStatus.RECORDING)
            
            // Report performance if initialization took too long
            val initDuration = System.currentTimeMillis() - startTime
            errorReporter.reportPerformanceIssue("startRecording", initDuration, 3000L)
            
            Log.i(TAG, "Recording started successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting recording", e)
            val error = RecordingError.AudioProcessingFailed
            errorReporter.reportCrash(e, "Unexpected error in startRecording", 
                mapOf("callInfo" to callInfo.toString()))
            errorHandler.handleError(error, showDialog = false)
            currentStatus.set(RecordingStatus.ERROR)
            false
        }
    }

    // Test-friendly API: remember call info on state changes
    fun onCallStateChanged(callInfo: CallInfo): Boolean {
        lastCallInfoById[callInfo.callId] = callInfo
        currentCallInfo.set(callInfo)
        return true
    }

    // Test-friendly API: start recording by callId (non-suspend)
    fun startRecording(callId: String): Boolean {
        val info = lastCallInfoById[callId] ?: return false
        return try {
            kotlinx.coroutines.runBlocking { startRecording(info) }
        } catch (_: Exception) { false }
    }

    // Test-friendly API: simulate call connected
    fun onCallConnected(callId: String) {
        serviceScope.launch {
            if (isAutoRecordingEnabled()) {
                startRecording(callId)
            }
        }
    }

    // Test-friendly API: simulate call ended
    fun onCallEnded(callId: String) {
        serviceScope.launch { stopRecording() }
    }

    // Test-friendly API: auto recording flag (can be mocked in tests)
    fun isAutoRecordingEnabled(): Boolean = false

    override suspend fun stopRecording(): RecordingResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Stopping recording")
        
        val stopStartTime = System.currentTimeMillis()
        
        try {
            if (!isRecording.get()) {
                val error = RecordingError.NoActiveRecording
                errorReporter.reportError(error, "stopRecording called with no active recording")
                Log.w(TAG, "No active recording to stop")
                return@withContext RecordingResult.Error(error, "録音が開始されていません")
            }
            
            // Update status
            currentStatus.set(RecordingStatus.STOPPING)
            
            // Get current recording information before stopping
            val callInfo = currentCallInfo.get()
            val recordingFile = currentRecordingFile.get()
            val startTime = recordingStartTime.get()
            
            if (callInfo == null || recordingFile == null || startTime == null) {
                val error = RecordingError.AudioProcessingFailed
                errorReporter.reportError(error, "Missing recording information during stop", 
                    mapOf(
                        "callInfo" to (callInfo?.toString() ?: "null"),
                        "recordingFile" to (recordingFile?.absolutePath ?: "null"),
                        "startTime" to (startTime?.toString() ?: "null")
                    ))
                Log.e(TAG, "Missing recording information")
                currentStatus.set(RecordingStatus.ERROR)
                resetRecordingState()
                return@withContext RecordingResult.Error(error, "録音情報が不完全です")
            }
            
            // Stop audio capture with error handling
            val processingResult = try {
                audioProcessor.stopCapture()
            } catch (e: Exception) {
                val error = RecordingError.AudioProcessingFailed
                errorReporter.reportError(error, "Exception during audio capture stop", 
                    mapOf("recordingFile" to recordingFile.absolutePath), e)
                
                // Clean up and reset state
                isRecording.set(false)
                recordingFile.delete()
                resetRecordingState()
                stopForeground(STOP_FOREGROUND_REMOVE)
                
                return@withContext RecordingResult.Error(error, "音声処理の停止中にエラーが発生しました")
            }
            
            // Update state
            isRecording.set(false)
            
            // Calculate duration
            val duration = System.currentTimeMillis() - startTime
            
            // Process the result with comprehensive error handling
            when (processingResult) {
                is com.callrecorder.pixel.audio.AudioProcessingResult.Success -> {
                    currentStatus.set(RecordingStatus.PROCESSING)
                    
                    // Validate file exists and has content
                    if (!recordingFile.exists() || recordingFile.length() == 0L) {
                        val error = RecordingError.FileCreationFailed
                        errorReporter.reportError(error, "Recording file is empty or missing after processing", 
                            mapOf(
                                "fileExists" to recordingFile.exists(),
                                "fileSize" to recordingFile.length(),
                                "filePath" to recordingFile.absolutePath
                            ))
                        
                        resetRecordingState()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        return@withContext RecordingResult.Error(error, "録音ファイルが空または見つかりません")
                    }
                    
                    // Save recording metadata with error handling
                    val metadata = RecordingMetadata(
                        callInfo = callInfo,
                        duration = duration,
                        fileSize = processingResult.fileSize,
                        audioQuality = processingResult.audioQuality
                    )
                    
                    val saved = try {
                        fileManager.saveRecording(recordingFile, metadata).also { ok ->
                            if (ok) repository?.let { repo ->
                                // Also persist via repository if provided (used by tests)
                                try { repo.insertRecording(
                                    Recording(
                                        id = java.util.UUID.randomUUID().toString(),
                                        fileName = recordingFile.name,
                                        filePath = recordingFile.absolutePath,
                                        contactName = callInfo.contactName,
                                        phoneNumber = callInfo.phoneNumber,
                                        duration = duration,
                                        fileSize = processingResult.fileSize,
                                        recordingDate = LocalDateTime.now(),
                                        audioQuality = processingResult.audioQuality,
                                        isIncoming = callInfo.isIncoming
                                    )
                                ) } catch (_: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        val error = RecordingError.FileCreationFailed
                        errorReporter.reportError(error, "Exception during metadata save", 
                            mapOf("metadata" to metadata.toString()), e)
                        
                        // Attempt recovery
                        val recoveryResult = errorRecovery.attemptRecovery(error)
                        if (recoveryResult.isSuccess) {
                            // Retry saving
                            try {
                                fileManager.saveRecording(recordingFile, metadata)
                            } catch (retryException: Exception) {
                                recordingFile.delete()
                                resetRecordingState()
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                return@withContext RecordingResult.Error(error, "録音の保存に失敗しました")
                            }
                        } else {
                            recordingFile.delete()
                            resetRecordingState()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            return@withContext RecordingResult.Error(error, "録音の保存に失敗しました")
                        }
                    }
                    
                    if (!saved) {
                        val error = RecordingError.FileCreationFailed
                        errorReporter.reportError(error, "Failed to save recording metadata - returned false")
                        Log.e(TAG, "Failed to save recording metadata")
                        currentStatus.set(RecordingStatus.ERROR)
                        recordingFile.delete()
                        resetRecordingState()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        return@withContext RecordingResult.Error(error, "録音の保存に失敗しました")
                    }
                    
                    // Create Recording object for result
                    val recording = Recording(
                        id = java.util.UUID.randomUUID().toString(),
                        fileName = recordingFile.name,
                        filePath = recordingFile.absolutePath,
                        contactName = callInfo.contactName,
                        phoneNumber = callInfo.phoneNumber,
                        duration = duration,
                        fileSize = processingResult.fileSize,
                        recordingDate = LocalDateTime.now(),
                        audioQuality = processingResult.audioQuality,
                        isIncoming = callInfo.isIncoming
                    )
                    
                    // Reset state
                    resetRecordingState()
                    
                    // Stop foreground service
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    
                    // Report performance if stopping took too long
                    val stopDuration = System.currentTimeMillis() - stopStartTime
                    errorReporter.reportPerformanceIssue("stopRecording", stopDuration, 5000L)
                    
                    Log.i(TAG, "Recording stopped and saved successfully")
                    RecordingResult.Success(recording)
                }
                
                is com.callrecorder.pixel.audio.AudioProcessingResult.Error -> {
                    val error = RecordingError.AudioProcessingFailed
                    errorReporter.reportError(error, "Audio processing failed during stop", 
                        mapOf("processingMessage" to processingResult.message))
                    
                    Log.e(TAG, "Audio processing failed: ${processingResult.message}")
                    currentStatus.set(RecordingStatus.ERROR)
                    
                    // Clean up failed recording file
                    try {
                        recordingFile.delete()
                    } catch (e: Exception) {
                        errorReporter.reportError(StorageError.FileDeletionFailed, 
                            "Failed to delete failed recording file", 
                            mapOf("filePath" to recordingFile.absolutePath), e)
                    }
                    
                    resetRecordingState()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    
                    RecordingResult.Error(error, processingResult.message)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error stopping recording", e)
            val error = RecordingError.AudioProcessingFailed
            errorReporter.reportCrash(e, "Unexpected error in stopRecording")
            
            currentStatus.set(RecordingStatus.ERROR)
            resetRecordingState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            
            RecordingResult.Error(error, "録音停止中に予期しないエラーが発生しました: ${e.message}")
        }
    }

    override fun isRecording(): Boolean {
        return isRecording.get()
    }

    override fun getRecordingStatus(): RecordingStatus {
        return currentStatus.get()
    }

    override fun getCurrentCallInfo(): CallInfo? {
        return currentCallInfo.get()
    }

    override fun getCurrentRecordingDuration(): Long {
        val startTime = recordingStartTime.get()
        return if (isRecording.get() && startTime != null) {
            System.currentTimeMillis() - startTime
        } else {
            0L
        }
    }

    override suspend fun pauseRecording(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Pausing recording")
        
        try {
            if (!isRecording.get() || currentStatus.get() != RecordingStatus.RECORDING) {
                Log.w(TAG, "Cannot pause - not in recording state")
                return@withContext false
            }
            
            val success = audioProcessor.pauseCapture()
            if (success) {
                currentStatus.set(RecordingStatus.PAUSED)
                currentCallInfo.get()?.let { callInfo ->
                    updateNotification(callInfo, RecordingStatus.PAUSED)
                }
                Log.i(TAG, "Recording paused")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing recording", e)
            false
        }
    }

    override suspend fun resumeRecording(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Resuming recording")
        
        try {
            if (!isRecording.get() || currentStatus.get() != RecordingStatus.PAUSED) {
                Log.w(TAG, "Cannot resume - not in paused state")
                return@withContext false
            }
            
            val success = audioProcessor.resumeCapture()
            if (success) {
                currentStatus.set(RecordingStatus.RECORDING)
                currentCallInfo.get()?.let { callInfo ->
                    updateNotification(callInfo, RecordingStatus.RECORDING)
                }
                Log.i(TAG, "Recording resumed")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming recording", e)
            false
        }
    }

    override suspend fun cancelRecording(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Cancelling recording")
        
        try {
            if (!isRecording.get()) {
                Log.w(TAG, "No active recording to cancel")
                return@withContext false
            }
            
            // Stop audio capture
            audioProcessor.stopCapture()
            
            // Delete the recording file
            currentRecordingFile.get()?.delete()
            
            // Reset state
            resetRecordingState()
            
            // Stop foreground service
            stopForeground(STOP_FOREGROUND_REMOVE)
            
            Log.i(TAG, "Recording cancelled")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling recording", e)
            false
        }
    }

    // Private helper methods

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "通話録音サービスの通知"
                setSound(null, null)
                enableVibration(false)
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createRecordingNotification(callInfo: CallInfo): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val contactName = callInfo.contactName ?: callInfo.phoneNumber
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("通話録音中")
            .setContentText("$contactName との通話を録音しています")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO: Use proper recording icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(callInfo: CallInfo, status: RecordingStatus) {
        val contactName = callInfo.contactName ?: callInfo.phoneNumber
        val statusText = when (status) {
            RecordingStatus.RECORDING -> "録音中"
            RecordingStatus.PAUSED -> "一時停止中"
            RecordingStatus.STOPPING -> "停止中"
            RecordingStatus.PROCESSING -> "処理中"
            else -> "録音中"
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("通話録音 - $statusText")
            .setContentText("$contactName との通話")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(status == RecordingStatus.RECORDING || status == RecordingStatus.PAUSED)
            .setSilent(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun resetRecordingState() {
        isRecording.set(false)
        currentStatus.set(RecordingStatus.IDLE)
        currentCallInfo.set(null)
        currentRecordingFile.set(null)
        recordingStartTime.set(null)
    }

    // Dependency injection setters (to be called by DI framework or factory)
    fun setAudioProcessor(audioProcessor: AudioProcessor) {
        this.audioProcessor = audioProcessor
    }

    fun setFileManager(fileManager: FileManager) {
        this.fileManager = fileManager
        // Reinitialize error recovery with the new file manager
        if (::errorReporter.isInitialized) {
            errorRecovery = ErrorRecovery(this, fileManager)
        }
    }

    fun setErrorHandler(errorHandler: ErrorHandler) {
        this.errorHandler = errorHandler
    }

    fun setErrorReporter(errorReporter: ErrorReporter) {
        this.errorReporter = errorReporter
    }

    fun setErrorRecovery(errorRecovery: ErrorRecovery) {
        this.errorRecovery = errorRecovery
    }
}
