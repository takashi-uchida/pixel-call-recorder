package com.callrecorder.pixel.service

import android.app.NotificationManager
import android.content.Context
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.callrecorder.pixel.audio.AudioProcessor
import com.callrecorder.pixel.audio.AudioProcessingResult
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.CallInfo
import com.callrecorder.pixel.error.RecordingError
import com.callrecorder.pixel.storage.FileManager
import com.callrecorder.pixel.storage.RecordingMetadata
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.LocalDateTime

/**
 * Integration tests for CallRecordingServiceImpl.
 * Tests the complete flow from call detection to recording completion.
 */
@ExperimentalCoroutinesApi
class CallRecordingServiceTest {

    @MockK
    private lateinit var mockContext: Context
    
    @MockK
    private lateinit var mockAudioProcessor: AudioProcessor
    
    @MockK
    private lateinit var mockFileManager: FileManager
    
    @MockK
    private lateinit var mockTelecomManager: TelecomManager
    
    @MockK
    private lateinit var mockTelephonyManager: TelephonyManager
    
    @MockK
    private lateinit var mockNotificationManager: NotificationManager
    
    @MockK
    private lateinit var mockRecordingFile: File

    private lateinit var service: CallRecordingServiceImpl
    private lateinit var testCallInfo: CallInfo

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        
        // Create test call info
        testCallInfo = CallInfo(
            callId = "test_call_123",
            phoneNumber = "+1234567890",
            contactName = "Test Contact",
            isIncoming = true,
            startTime = LocalDateTime.now()
        )

        // Mock context system services
        every { mockContext.getSystemService(Context.TELECOM_SERVICE) } returns mockTelecomManager
        every { mockContext.getSystemService(Context.TELEPHONY_SERVICE) } returns mockTelephonyManager
        every { mockContext.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockNotificationManager
        
        // Mock notification manager
        every { mockNotificationManager.createNotificationChannel(any()) } just Runs
        every { mockNotificationManager.notify(any(), any()) } just Runs

        // Create service instance
        service = CallRecordingServiceImpl()
        
        // Set up service with mocked dependencies
        service.setAudioProcessor(mockAudioProcessor)
        service.setFileManager(mockFileManager)
        
        // Mock service lifecycle methods
        mockkObject(service)
        every { service.startForeground(any(), any()) } just Runs
        every { service.stopForeground(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `startRecording should successfully start recording when conditions are met`() = runTest {
        // Arrange
        every { mockFileManager.createRecordingFile(testCallInfo) } returns mockRecordingFile
        coEvery { mockAudioProcessor.initializeAudioCapture(any()) } returns true
        coEvery { mockAudioProcessor.startCapture(mockRecordingFile) } returns true
        every { mockRecordingFile.name } returns "test_recording.m4a"
        every { mockRecordingFile.absolutePath } returns "/test/path/test_recording.m4a"

        // Act
        val result = service.startRecording(testCallInfo)

        // Assert
        assertTrue("Recording should start successfully", result)
        assertTrue("Service should be in recording state", service.isRecording())
        assertEquals("Status should be RECORDING", RecordingStatus.RECORDING, service.getRecordingStatus())
        assertEquals("Current call info should match", testCallInfo, service.getCurrentCallInfo())
        
        // Verify interactions
        coVerify { mockFileManager.createRecordingFile(testCallInfo) }
        coVerify { mockAudioProcessor.initializeAudioCapture(any()) }
        coVerify { mockAudioProcessor.startCapture(mockRecordingFile) }
    }

    @Test
    fun `startRecording should fail when already recording`() = runTest {
        // Arrange - start first recording
        every { mockFileManager.createRecordingFile(any()) } returns mockRecordingFile
        coEvery { mockAudioProcessor.initializeAudioCapture(any()) } returns true
        coEvery { mockAudioProcessor.startCapture(any()) } returns true
        service.startRecording(testCallInfo)

        // Act - try to start second recording
        val result = service.startRecording(testCallInfo.copy(callId = "different_call"))

        // Assert
        assertFalse("Second recording should fail", result)
        assertTrue("Service should still be recording first call", service.isRecording())
    }

    @Test
    fun `startRecording should fail when file creation fails`() = runTest {
        // Arrange
        every { mockFileManager.createRecordingFile(testCallInfo) } returns null

        // Act
        val result = service.startRecording(testCallInfo)

        // Assert
        assertFalse("Recording should fail when file creation fails", result)
        assertFalse("Service should not be recording", service.isRecording())
        assertEquals("Status should be ERROR", RecordingStatus.ERROR, service.getRecordingStatus())
    }

    @Test
    fun `startRecording should fail when audio initialization fails`() = runTest {
        // Arrange
        every { mockFileManager.createRecordingFile(testCallInfo) } returns mockRecordingFile
        coEvery { mockAudioProcessor.initializeAudioCapture(any()) } returns false

        // Act
        val result = service.startRecording(testCallInfo)

        // Assert
        assertFalse("Recording should fail when audio initialization fails", result)
        assertFalse("Service should not be recording", service.isRecording())
        assertEquals("Status should be ERROR", RecordingStatus.ERROR, service.getRecordingStatus())
    }

    @Test
    fun `stopRecording should successfully complete recording`() = runTest {
        // Arrange - start recording first
        every { mockFileManager.createRecordingFile(testCallInfo) } returns mockRecordingFile
        coEvery { mockAudioProcessor.initializeAudioCapture(any()) } returns true
        coEvery { mockAudioProcessor.startCapture(mockRecordingFile) } returns true
        every { mockRecordingFile.name } returns "test_recording.m4a"
        every { mockRecordingFile.absolutePath } returns "/test/path/test_recording.m4a"
        
        service.startRecording(testCallInfo)

        // Mock successful audio processing result
        val successResult = AudioProcessingResult.Success(
            outputFile = mockRecordingFile,
            duration = 30000L,
            fileSize = 1024000L,
            audioQuality = AudioQuality.STANDARD
        )
        coEvery { mockAudioProcessor.stopCapture() } returns successResult
        coEvery { mockFileManager.saveRecording(any(), any()) } returns true

        // Act
        val result = service.stopRecording()

        // Assert
        assertTrue("Stop recording should succeed", result is RecordingResult.Success)
        assertFalse("Service should not be recording", service.isRecording())
        assertEquals("Status should be IDLE", RecordingStatus.IDLE, service.getRecordingStatus())
        assertNull("Current call info should be null", service.getCurrentCallInfo())

        // Verify interactions
        coVerify { mockAudioProcessor.stopCapture() }
        coVerify { mockFileManager.saveRecording(mockRecordingFile, any()) }
    }

    @Test
    fun `stopRecording should fail when no active recording`() = runTest {
        // Act
        val result = service.stopRecording()

        // Assert
        assertTrue("Result should be error", result is RecordingResult.Error)
        val errorResult = result as RecordingResult.Error
        assertEquals("Error should be NoActiveRecording", RecordingError.NoActiveRecording, errorResult.error)
    }

    @Test
    fun `stopRecording should handle audio processing failure`() = runTest {
        // Arrange - start recording first
        every { mockFileManager.createRecordingFile(testCallInfo) } returns mockRecordingFile
        coEvery { mockAudioProcessor.initializeAudioCapture(any()) } returns true
        coEvery { mockAudioProcessor.startCapture(mockRecordingFile) } returns true
        every { mockRecordingFile.name } returns "test_recording.m4a"
        every { mockRecordingFile.absolutePath } returns "/test/path/test_recording.m4a"
        every { mockRecordingFile.delete() } returns true
        
        service.startRecording(testCallInfo)

        // Mock failed audio processing result
        val errorResult = AudioProcessingResult.Error(
            error = com.callrecorder.pixel.audio.AudioProcessingError.ENCODING_FAILED,
            message = "Encoding failed"
        )
        coEvery { mockAudioProcessor.stopCapture() } returns errorResult

        // Act
        val result = service.stopRecording()

        // Assert
        assertTrue("Result should be error", result is RecordingResult.Error)
        val error = result as RecordingResult.Error
        assertEquals("Error should be AudioProcessingFailed", RecordingError.AudioProcessingFailed, error.error)
        assertFalse("Service should not be recording", service.isRecording())
        
        // Verify file cleanup
        verify { mockRecordingFile.delete() }
    }

    @Test
    fun `pauseRecording should successfully pause active recording`() = runTest {
        // Arrange - start recording first
        every { mockFileManager.createRecordingFile(testCallInfo) } returns mockRecordingFile
        coEvery { mockAudioProcessor.initializeAudioCapture(any()) } returns true
        coEvery { mockAudioProcessor.startCapture(mockRecordingFile) } returns true
        coEvery { mockAudioProcessor.pauseCapture() } returns true
        every { mockRecordingFile.name } returns "test_recording.m4a"
        every { mockRecordingFile.absolutePath } returns "/test/path/test_recording.m4a"
        
        service.startRecording(testCallInfo)

        // Act
        val result = service.pauseRecording()

        // Assert
        assertTrue("Pause should succeed", result)
        assertTrue("Service should still be recording", service.isRecording())
        assertEquals("Status should be PAUSED", RecordingStatus.PAUSED, service.getRecordingStatus())
        
        // Verify interaction
        coVerify { mockAudioProcessor.pauseCapture() }
    }

    @Test
    fun `resumeRecording should successfully resume paused recording`() = runTest {
        // Arrange - start and pause recording first
        every { mockFileManager.createRecordingFile(testCallInfo) } returns mockRecordingFile
        coEvery { mockAudioProcessor.initializeAudioCapture(any()) } returns true
        coEvery { mockAudioProcessor.startCapture(mockRecordingFile) } returns true
        coEvery { mockAudioProcessor.pauseCapture() } returns true
        coEvery { mockAudioProcessor.resumeCapture() } returns true
        every { mockRecordingFile.name } returns "test_recording.m4a"
        every { mockRecordingFile.absolutePath } returns "/test/path/test_recording.m4a"
        
        service.startRecording(testCallInfo)
        service.pauseRecording()

        // Act
        val result = service.resumeRecording()

        // Assert
        assertTrue("Resume should succeed", result)
        assertTrue("Service should be recording", service.isRecording())
        assertEquals("Status should be RECORDING", RecordingStatus.RECORDING, service.getRecordingStatus())
        
        // Verify interaction
        coVerify { mockAudioProcessor.resumeCapture() }
    }

    @Test
    fun `cancelRecording should successfully cancel active recording`() = runTest {
        // Arrange - start recording first
        every { mockFileManager.createRecordingFile(testCallInfo) } returns mockRecordingFile
        coEvery { mockAudioProcessor.initializeAudioCapture(any()) } returns true
        coEvery { mockAudioProcessor.startCapture(mockRecordingFile) } returns true
        coEvery { mockAudioProcessor.stopCapture() } returns AudioProcessingResult.Success(
            mockRecordingFile, 0L, 0L, AudioQuality.STANDARD
        )
        every { mockRecordingFile.name } returns "test_recording.m4a"
        every { mockRecordingFile.absolutePath } returns "/test/path/test_recording.m4a"
        every { mockRecordingFile.delete() } returns true
        
        service.startRecording(testCallInfo)

        // Act
        val result = service.cancelRecording()

        // Assert
        assertTrue("Cancel should succeed", result)
        assertFalse("Service should not be recording", service.isRecording())
        assertEquals("Status should be IDLE", RecordingStatus.IDLE, service.getRecordingStatus())
        assertNull("Current call info should be null", service.getCurrentCallInfo())
        
        // Verify file deletion
        verify { mockRecordingFile.delete() }
    }

    @Test
    fun `getCurrentRecordingDuration should return correct duration`() = runTest {
        // Arrange - start recording
        every { mockFileManager.createRecordingFile(testCallInfo) } returns mockRecordingFile
        coEvery { mockAudioProcessor.initializeAudioCapture(any()) } returns true
        coEvery { mockAudioProcessor.startCapture(mockRecordingFile) } returns true
        every { mockRecordingFile.name } returns "test_recording.m4a"
        every { mockRecordingFile.absolutePath } returns "/test/path/test_recording.m4a"
        
        service.startRecording(testCallInfo)
        
        // Wait a bit to simulate recording time
        Thread.sleep(100)

        // Act
        val duration = service.getCurrentRecordingDuration()

        // Assert
        assertTrue("Duration should be positive", duration > 0)
        assertTrue("Duration should be reasonable", duration < 1000) // Less than 1 second for this test
    }

    @Test
    fun `service should handle multiple start-stop cycles correctly`() = runTest {
        // Arrange
        every { mockFileManager.createRecordingFile(any()) } returns mockRecordingFile
        coEvery { mockAudioProcessor.initializeAudioCapture(any()) } returns true
        coEvery { mockAudioProcessor.startCapture(mockRecordingFile) } returns true
        every { mockRecordingFile.name } returns "test_recording.m4a"
        every { mockRecordingFile.absolutePath } returns "/test/path/test_recording.m4a"
        
        val successResult = AudioProcessingResult.Success(
            outputFile = mockRecordingFile,
            duration = 30000L,
            fileSize = 1024000L,
            audioQuality = AudioQuality.STANDARD
        )
        coEvery { mockAudioProcessor.stopCapture() } returns successResult
        coEvery { mockFileManager.saveRecording(any(), any()) } returns true

        // Act & Assert - First cycle
        assertTrue("First recording should start", service.startRecording(testCallInfo))
        assertTrue("Should be recording", service.isRecording())
        
        val firstResult = service.stopRecording()
        assertTrue("First recording should stop successfully", firstResult is RecordingResult.Success)
        assertFalse("Should not be recording", service.isRecording())

        // Act & Assert - Second cycle
        val secondCallInfo = testCallInfo.copy(callId = "second_call", phoneNumber = "+0987654321")
        assertTrue("Second recording should start", service.startRecording(secondCallInfo))
        assertTrue("Should be recording again", service.isRecording())
        assertEquals("Should have new call info", secondCallInfo, service.getCurrentCallInfo())
        
        val secondResult = service.stopRecording()
        assertTrue("Second recording should stop successfully", secondResult is RecordingResult.Success)
        assertFalse("Should not be recording", service.isRecording())
    }
}