package com.example.pixelcallrecorder.service

import android.content.Context
import android.media.MediaRecorder
import android.telephony.TelephonyManager
import com.example.pixelcallrecorder.utils.FileUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class CallRecorderServiceTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockTelephonyManager: TelephonyManager

    @Mock
    private lateinit var mockMediaRecorder: MediaRecorder

    private lateinit var service: CallRecorderService

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        service = CallRecorderService()

        // Mock getSystemService to return our mock TelephonyManager
        whenever(mockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mockTelephonyManager)

        // Use reflection to set the context and mediaRecorder in the service
        val contextField = Service::class.java.getDeclaredField("mBase")
        contextField.isAccessible = true
        contextField.set(service, mockContext)

        // Mock FileUtils.getRecordingFilePath
        whenever(FileUtils.getRecordingFilePath(any())).thenReturn("/mock/path/recording.3gp")
    }

    @Test
    fun `onCreate should register phone state listener`() {
        service.onCreate()
        verify(mockTelephonyManager).listen(any(), eq(PhoneStateListener.LISTEN_CALL_STATE))
    }

    @Test
    fun `onDestroy should unregister phone state listener`() {
        service.onDestroy()
        verify(mockTelephonyManager).listen(any(), eq(PhoneStateListener.LISTEN_NONE))
    }

    @Test
    fun `startRecording should initialize and start media recorder`() {
        // Use reflection to set the mediaRecorder in the service
        val mediaRecorderField = CallRecorderService::class.java.getDeclaredField("mediaRecorder")
        mediaRecorderField.isAccessible = true
        mediaRecorderField.set(service, mockMediaRecorder)

        // Directly call the private method using reflection
        val startRecordingMethod = CallRecorderService::class.java.getDeclaredMethod("startRecording")
        startRecordingMethod.isAccessible = true
        startRecordingMethod.invoke(service)

        verify(mockMediaRecorder).setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        verify(mockMediaRecorder).setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        verify(mockMediaRecorder).setOutputFile(any<String>())
        verify(mockMediaRecorder).setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        verify(mockMediaRecorder).prepare()
        verify(mockMediaRecorder).start()
    }

    @Test
    fun `stopRecording should stop and release media recorder`() {
        // Use reflection to set the mediaRecorder in the service
        val mediaRecorderField = CallRecorderService::class.java.getDeclaredField("mediaRecorder")
        mediaRecorderField.isAccessible = true
        mediaRecorderField.set(service, mockMediaRecorder)

        // Directly call the private method using reflection
        val stopRecordingMethod = CallRecorderService::class.java.getDeclaredMethod("stopRecording")
        stopRecordingMethod.isAccessible = true
        stopRecordingMethod.invoke(service)

        verify(mockMediaRecorder).stop()
        verify(mockMediaRecorder).release()
        // Verify mediaRecorder is set to null
        val currentMediaRecorder = mediaRecorderField.get(service)
        assert(currentMediaRecorder == null)
    }
}