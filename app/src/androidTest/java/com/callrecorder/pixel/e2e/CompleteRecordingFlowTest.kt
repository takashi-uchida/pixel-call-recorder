package com.callrecorder.pixel.e2e

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.callrecorder.pixel.audio.AudioProcessingResult
import com.callrecorder.pixel.audio.MediaRecorderAudioProcessor
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.CallInfo
import com.callrecorder.pixel.service.CallRecordingServiceImpl
import com.callrecorder.pixel.storage.FileManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class CompleteRecordingFlowTest {

    @Test
    fun endToEnd_recording_start_and_stop_with_mocks() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val audioProcessor = mockk<MediaRecorderAudioProcessor>(relaxed = true)
        val fileManager = mockk<FileManager>()

        val service = CallRecordingServiceImpl(
            context,
            audioProcessor,
            fileManager,
            mockk(relaxed = true),
            mockk(relaxed = true)
        )

        val callInfo = CallInfo(
            callId = "mock_call",
            phoneNumber = "+8100000000",
            contactName = "テスト",
            isIncoming = true,
            startTime = LocalDateTime.now()
        )

        val outFile = File.createTempFile("recording", ".m4a")
        every { fileManager.createRecordingFile(callInfo) } returns outFile
        coEvery { audioProcessor.initializeAudioCapture(any()) } returns true
        coEvery { audioProcessor.startCapture(outFile) } returns true
        coEvery { audioProcessor.stopCapture() } returns AudioProcessingResult.Success(
            outputFile = outFile,
            duration = 1000L,
            fileSize = 1234L,
            audioQuality = AudioQuality.STANDARD
        )
        every { fileManager.saveRecording(outFile, any()) } returns true

        // Remember call and start/stop
        assertTrue(service.onCallStateChanged(callInfo))
        assertTrue(service.startRecording(callInfo.callId))
        val result = kotlinx.coroutines.runBlocking { service.stopRecording() }
        assertTrue(result.isSuccess)
        assertFalse(service.isRecording())

        outFile.delete()
    }
}

