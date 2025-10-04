package com.callrecorder.pixel.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.Recording
import com.callrecorder.pixel.data.repository.RecordingRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime

/**
 * Unit tests for RecordingListActivity
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecordingListActivityTest {

    private lateinit var activity: RecordingListActivity
    private lateinit var mockRepository: RecordingRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockRepository = mockk()
        
        // Mock the repository singleton
        mockkObject(RecordingRepository.Companion)
        every { RecordingRepository.getInstance(any()) } returns mockRepository
        
        activity = Robolectric.buildActivity(RecordingListActivity::class.java)
            .create()
            .get()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should load recordings on create`() = runBlocking {
        // Given
        val recordings = createSampleRecordings()
        coEvery { mockRepository.getAllRecordings() } returns recordings

        // When
        activity.onCreate(null)

        // Then
        coVerify { mockRepository.getAllRecordings() }
    }

    @Test
    fun `should handle empty recordings list`() = runBlocking {
        // Given
        coEvery { mockRepository.getAllRecordings() } returns emptyList()

        // When
        activity.onCreate(null)

        // Then
        coVerify { mockRepository.getAllRecordings() }
        // Verify empty state is shown (would need UI testing framework for full verification)
    }

    @Test
    fun `should handle repository error gracefully`() = runBlocking {
        // Given
        coEvery { mockRepository.getAllRecordings() } throws RuntimeException("Database error")

        // When
        activity.onCreate(null)

        // Then
        coVerify { mockRepository.getAllRecordings() }
        // Verify error handling (would need UI testing framework for full verification)
    }

    @Test
    fun `should delete recording when requested`() = runBlocking {
        // Given
        val recording = createSampleRecording()
        coEvery { mockRepository.deleteRecording(recording.id) } returns true
        coEvery { mockRepository.getAllRecordings() } returns listOf(recording) andThen emptyList()

        // When
        activity.onDeleteRequested(recording)

        // Then
        coVerify { mockRepository.deleteRecording(recording.id) }
    }

    @Test
    fun `should handle delete failure`() = runBlocking {
        // Given
        val recording = createSampleRecording()
        coEvery { mockRepository.deleteRecording(recording.id) } returns false

        // When
        activity.onDeleteRequested(recording)

        // Then
        coVerify { mockRepository.deleteRecording(recording.id) }
        // Verify error message is shown (would need UI testing framework for full verification)
    }

    @Test
    fun `should get recording file for sharing`() = runBlocking {
        // Given
        val recording = createSampleRecording()
        val mockFile = mockk<File>()
        every { mockFile.exists() } returns true
        coEvery { mockRepository.getRecordingFile(recording.id) } returns mockFile

        // When
        activity.onShareRequested(recording)

        // Then
        coVerify { mockRepository.getRecordingFile(recording.id) }
    }

    @Test
    fun `should handle missing file for sharing`() = runBlocking {
        // Given
        val recording = createSampleRecording()
        coEvery { mockRepository.getRecordingFile(recording.id) } returns null

        // When
        activity.onShareRequested(recording)

        // Then
        coVerify { mockRepository.getRecordingFile(recording.id) }
        // Verify error message is shown (would need UI testing framework for full verification)
    }

    private fun createSampleRecordings(): List<Recording> {
        return listOf(
            createSampleRecording("1", "田中太郎", "090-1234-5678"),
            createSampleRecording("2", "佐藤花子", "080-9876-5432"),
            createSampleRecording("3", null, "070-1111-2222")
        )
    }

    private fun createSampleRecording(
        id: String = "test-id",
        contactName: String? = "テスト連絡先",
        phoneNumber: String = "090-1234-5678"
    ): Recording {
        return Recording(
            id = id,
            fileName = "recording_$id.m4a",
            filePath = "/storage/recordings/recording_$id.m4a",
            contactName = contactName,
            phoneNumber = phoneNumber,
            duration = 180000L, // 3 minutes
            fileSize = 2048000L, // 2MB
            recordingDate = LocalDateTime.now(),
            audioQuality = AudioQuality.STANDARD,
            isIncoming = true
        )
    }
}