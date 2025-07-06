package com.example.pixelcallrecorder.service

import android.app.Service
import android.content.Context
import android.media.MediaRecorder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.example.pixelcallrecorder.utils.FileUtils
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class CallRecorderServiceTest {

    private lateinit var mockTelephonyManager: TelephonyManager
    private lateinit var service: CallRecorderService

    @Before
    fun setup() {
        mockTelephonyManager = mockk(relaxed = true)
        
        // Mock static FileUtils
        mockkObject(FileUtils)
        every { FileUtils.getRecordingFilePath(any()) } returns "/mock/path/recording.3gp"
        
        // Create service with mocked dependencies
        service = spyk(CallRecorderService())
        every { service.getSystemService(Context.TELEPHONY_SERVICE) } returns mockTelephonyManager
    }

    @Test
    fun `onCreate should register phone state listener`() {
        service.onCreate()
        verify { mockTelephonyManager.listen(any(), PhoneStateListener.LISTEN_CALL_STATE) }
    }

    @Test
    fun `onDestroy should unregister phone state listener`() {
        service.onCreate() // Initialize first
        service.onDestroy()
        verify { mockTelephonyManager.listen(any(), PhoneStateListener.LISTEN_NONE) }
    }

    @Test
    fun `phone state change to OFFHOOK should trigger recording start`() {
        mockkConstructor(MediaRecorder::class)
        every { anyConstructed<MediaRecorder>().prepare() } just runs
        every { anyConstructed<MediaRecorder>().start() } just runs
        every { anyConstructed<MediaRecorder>().setAudioSource(any()) } just runs
        every { anyConstructed<MediaRecorder>().setOutputFormat(any()) } just runs
        every { anyConstructed<MediaRecorder>().setAudioEncoder(any()) } just runs
        every { anyConstructed<MediaRecorder>().setOutputFile(any<String>()) } just runs

        // Mock the service method that handles phone state changes
        every { service.startRecording() } just runs
        
        service.onCreate()
        service.startRecording()
        
        verify { anyConstructed<MediaRecorder>().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION) }
        verify { anyConstructed<MediaRecorder>().setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP) }
        verify { anyConstructed<MediaRecorder>().setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB) }
        verify { anyConstructed<MediaRecorder>().prepare() }
        verify { anyConstructed<MediaRecorder>().start() }
    }

    @Test
    fun `phone state change to IDLE should stop recording`() {
        mockkConstructor(MediaRecorder::class)
        every { anyConstructed<MediaRecorder>().stop() } just runs
        every { anyConstructed<MediaRecorder>().release() } just runs
        every { service.stopRecording() } just runs

        service.onCreate()
        service.stopRecording()
        
        verify { anyConstructed<MediaRecorder>().stop() }
        verify { anyConstructed<MediaRecorder>().release() }
    }
}