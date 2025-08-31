package com.callrecorder.pixel.integration

import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.callrecorder.pixel.audio.AudioProcessor
import com.callrecorder.pixel.audio.AudioProcessingResult
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.CallInfo
import com.callrecorder.pixel.receiver.PhoneStateReceiver
import com.callrecorder.pixel.service.CallRecordingServiceImpl
import com.callrecorder.pixel.service.RecordingResult
import com.callrecorder.pixel.service.RecordingStatus
import com.callrecorder.pixel.storage.FileManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * End-to-end integration tests for the complete call recording flow.
 * Tests the interaction between PhoneStateReceiver and CallRecordingService.
 */
@ExperimentalCoroutinesApi
class CallRecordingIntegrationTest {

    @MockK
    private lateinit var mockContext: Context
    
    @MockK
    private lateinit var mockAudioProcessor: AudioProcessor
    
    @MockK
    private lateinit var mockFileManager: FileManager
    
    @MockK
    private lateinit var mockRecordingFile: File

    private lateinit var phoneStateReceiver: PhoneStateReceiver
    private lateinit var recordingService: CallRecordingServiceImpl
    private val capturedServiceIntents = mutableListOf<Intent>()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        
        phoneStateReceiver = PhoneStateReceiver()
        recordingService = CallRecordingServiceImpl()
        
        // Set up service dependencies
        recordingService.setAudioProcessor(mockAudioProcessor)
        recordingService.setFileManager(mockFileManager)
        
        // Mock service lifecycle methods
        mockkObject(recordingService)
        every { recordingService.startForeground(any(), any()) } just Runs
        every { recordingService.stopForeground(any()) } just Runs
        
        // Mock context to capture service intents
        every { mockContext.startForegroundService(capture(capturedServiceIntents)) } returns null
        every { mockContext.startService(capture(capturedServiceIntents)) } returns null
        
        // Mock system services
        every { mockContext.getSystemService(any()) } returns mockk(relaxed = true)
        
        capturedServiceIntents.clear()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `complete incoming call recording flow should work end-to-end`() = runTest {
        // Arrange
        val phoneNumber = "+1234567890"
        setupSuccessfulRecordingMocks()

        // Act - Simulate incoming call flow
        
        // 1. Phone starts ringing
        val ringingIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
            putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, phoneNumber)
        }
        phoneStateReceiver.onReceive(mockContext, ringingIntent)
        
        // 2. Call is answered (goes off hook)
        val offHookIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK)
        }
        phoneStateReceiver.onReceive(mockContext, offHookIntent)
        
        // 3. Simulate service receiving the start recording intent
        assertEquals("Should have sent start recording intent", 1, capturedServiceIntents.size)
        val startIntent = capturedServiceIntents[0]
        assertEquals("Should be start recording action", 
            CallRecordingServiceImpl.ACTION_START_RECORDING, startIntent.action)
        
        val callInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            startIntent.getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO, CallInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            startIntent.getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO)
        }
        
        assertNotNull("Call info should be present", callInfo)
        val startResult = recordingService.startRecording(callInfo!!)
        assertTrue("Recording should start successfully", startResult)
        assertTrue("Service should be recording", recordingService.isRecording())
        assertEquals("Status should be RECORDING", RecordingStatus.RECORDING, recordingService.getRecordingStatus())
        
        // 4. Call ends (goes idle)
        capturedServiceIntents.clear()
        val idleIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE)
        }
        phoneStateReceiver.onReceive(mockContext, idleIntent)
        
        // 5. Simulate service receiving the stop recording intent
        assertEquals("Should have sent stop recording intent", 1, capturedServiceIntents.size)
        val stopIntent = capturedServiceIntents[0]
        assertEquals("Should be stop recording action", 
            CallRecordingServiceImpl.ACTION_STOP_RECORDING, stopIntent.action)
        
        val stopResult = recordingService.stopRecording()
        assertTrue("Recording should stop successfully", stopResult is RecordingResult.Success)
        assertFalse("Service should not be recording", recordingService.isRecording())
        assertEquals("Status should be IDLE", RecordingStatus.IDLE, recordingService.getRecordingStatus())

        // Assert - Verify all interactions occurred
        coVerify { mockFileManager.createRecordingFile(any()) }
        coVerify { mockAudioProcessor.initializeAudioCapture(any()) }
        coVerify { mockAudioProcessor.startCapture(any()) }
        coVerify { mockAudioProcessor.stopCapture() }
        coVerify { mockFileManager.saveRecording(any(), any()) }
    }

    @Test
    fun `complete outgoing call recording flow should work end-to-end`() = runTest {
        // Arrange
        val phoneNumber = "+0987654321"
        setupSuccessfulRecordingMocks()

        // Act - Simulate outgoing call flow
        
        // 1. Outgoing call initiated
        val outgoingIntent = Intent(Intent.ACTION_NEW_OUTGOING_CALL).apply {
            putExtra(Intent.EXTRA_PHONE_NUMBER, phoneNumber)
        }
        phoneStateReceiver.onReceive(mockContext, outgoingIntent)
        
        // 2. Call connects (goes off hook)
        val offHookIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK)
        }
        phoneStateReceiver.onReceive(mockContext, offHookIntent)
        
        // 3. Verify recording started
        assertEquals("Should have sent start recording intent", 1, capturedServiceIntents.size)
        val startIntent = capturedServiceIntents[0]
        
        val callInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            startIntent.getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO, CallInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            startIntent.getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO)
        }
        
        assertNotNull("Call info should be present", callInfo)
        assertEquals("Should have correct phone number", phoneNumber, callInfo?.phoneNumber)
        assertFalse("Should be outgoing call", callInfo?.isIncoming == true)
        
        val startResult = recordingService.startRecording(callInfo!!)
        assertTrue("Recording should start successfully", startResult)
        
        // 4. Call ends
        capturedServiceIntents.clear()
        val idleIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE)
        }
        phoneStateReceiver.onReceive(mockContext, idleIntent)
        
        // 5. Verify recording stopped
        assertEquals("Should have sent stop recording intent", 1, capturedServiceIntents.size)
        val stopResult = recordingService.stopRecording()
        assertTrue("Recording should stop successfully", stopResult is RecordingResult.Success)
    }

    @Test
    fun `service restart during recording should maintain state`() = runTest {
        // Arrange
        val phoneNumber = "+1111111111"
        setupSuccessfulRecordingMocks()

        // Start recording
        val callInfo = CallInfo(
            callId = "test_call",
            phoneNumber = phoneNumber,
            contactName = null,
            isIncoming = true,
            startTime = java.time.LocalDateTime.now()
        )
        
        recordingService.startRecording(callInfo)
        assertTrue("Should be recording", recordingService.isRecording())
        
        // Simulate service restart by creating new instance
        val restartedService = CallRecordingServiceImpl()
        restartedService.setAudioProcessor(mockAudioProcessor)
        restartedService.setFileManager(mockFileManager)
        
        // Mock service lifecycle methods for restarted service
        mockkObject(restartedService)
        every { restartedService.startForeground(any(), any()) } just Runs
        every { restartedService.stopForeground(any()) } just Runs
        
        // Verify new service starts in idle state
        assertFalse("Restarted service should not be recording", restartedService.isRecording())
        assertEquals("Status should be IDLE", RecordingStatus.IDLE, restartedService.getRecordingStatus())
        
        // Should be able to start new recording
        val newCallInfo = callInfo.copy(callId = "new_call")
        val result = restartedService.startRecording(newCallInfo)
        assertTrue("Should be able to start new recording", result)
    }

    @Test
    fun `multiple rapid call state changes should be handled correctly`() = runTest {
        // Arrange
        val phoneNumber = "+2222222222"
        setupSuccessfulRecordingMocks()

        // Act - Simulate rapid state changes
        val ringingIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
            putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, phoneNumber)
        }
        
        val offHookIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK)
        }
        
        val idleIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE)
        }

        // Send multiple rapid state changes
        phoneStateReceiver.onReceive(mockContext, ringingIntent)
        phoneStateReceiver.onReceive(mockContext, offHookIntent)
        phoneStateReceiver.onReceive(mockContext, ringingIntent) // Should be ignored
        phoneStateReceiver.onReceive(mockContext, offHookIntent) // Should be ignored
        phoneStateReceiver.onReceive(mockContext, idleIntent)

        // Assert - Should only have start and stop intents
        assertEquals("Should have exactly 2 service intents", 2, capturedServiceIntents.size)
        assertEquals("First should be start recording", 
            CallRecordingServiceImpl.ACTION_START_RECORDING, capturedServiceIntents[0].action)
        assertEquals("Second should be stop recording", 
            CallRecordingServiceImpl.ACTION_STOP_RECORDING, capturedServiceIntents[1].action)
    }

    @Test
    fun `recording failure should not affect subsequent calls`() = runTest {
        // Arrange
        val phoneNumber = "+3333333333"
        setupFailingRecordingMocks()

        // Act - First call that fails
        val ringingIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
            putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, phoneNumber)
        }
        
        val offHookIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK)
        }

        phoneStateReceiver.onReceive(mockContext, ringingIntent)
        phoneStateReceiver.onReceive(mockContext, offHookIntent)
        
        val callInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            capturedServiceIntents[0].getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO, CallInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            capturedServiceIntents[0].getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO)
        }
        
        val failResult = recordingService.startRecording(callInfo!!)
        assertFalse("First recording should fail", failResult)
        
        // Setup successful mocks for second call
        setupSuccessfulRecordingMocks()
        capturedServiceIntents.clear()
        
        // Second call that should succeed
        val secondRingingIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
            putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, "+4444444444")
        }
        
        phoneStateReceiver.onReceive(mockContext, secondRingingIntent)
        phoneStateReceiver.onReceive(mockContext, offHookIntent)
        
        val secondCallInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            capturedServiceIntents[0].getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO, CallInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            capturedServiceIntents[0].getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO)
        }
        
        val successResult = recordingService.startRecording(secondCallInfo!!)
        assertTrue("Second recording should succeed", successResult)
    }

    private fun setupSuccessfulRecordingMocks() {
        every { mockFileManager.createRecordingFile(any()) } returns mockRecordingFile
        coEvery { mockAudioProcessor.initializeAudioCapture(any()) } returns true
        coEvery { mockAudioProcessor.startCapture(any()) } returns true
        coEvery { mockAudioProcessor.stopCapture() } returns AudioProcessingResult.Success(
            outputFile = mockRecordingFile,
            duration = 30000L,
            fileSize = 1024000L,
            audioQuality = AudioQuality.STANDARD
        )
        coEvery { mockFileManager.saveRecording(any(), any()) } returns true
        every { mockRecordingFile.name } returns "test_recording.m4a"
        every { mockRecordingFile.absolutePath } returns "/test/path/test_recording.m4a"
    }

    private fun setupFailingRecordingMocks() {
        every { mockFileManager.createRecordingFile(any()) } returns null
    }
}