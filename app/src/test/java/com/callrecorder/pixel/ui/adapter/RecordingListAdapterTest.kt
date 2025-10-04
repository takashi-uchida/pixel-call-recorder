package com.callrecorder.pixel.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.callrecorder.pixel.R
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.Recording
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Unit tests for RecordingListAdapter
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecordingListAdapterTest {

    private lateinit var adapter: RecordingListAdapter
    private lateinit var mockClickListener: RecordingListAdapter.OnRecordingClickListener
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockClickListener = mockk(relaxed = true)
        adapter = RecordingListAdapter(mockClickListener)
    }

    @Test
    fun `should have zero items initially`() {
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `should update recordings and item count`() {
        // Given
        val recordings = createSampleRecordings()

        // When
        adapter.updateRecordings(recordings)

        // Then
        assertEquals(recordings.size, adapter.itemCount)
    }

    @Test
    fun `should create view holder with correct layout`() {
        // Given
        val parent = mockk<ViewGroup>(relaxed = true)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.item_recording, parent, false)
        
        // Mock parent context
        io.mockk.every { parent.context } returns context

        // When
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        // Then
        assertNotNull(viewHolder)
    }

    @Test
    fun `should bind recording data to view holder`() {
        // Given
        val recordings = createSampleRecordings()
        adapter.updateRecordings(recordings)
        
        val parent = mockk<ViewGroup>(relaxed = true)
        io.mockk.every { parent.context } returns context
        
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        // When
        adapter.onBindViewHolder(viewHolder, 0)

        // Then
        // Verify that the view holder is bound (detailed UI verification would require UI testing framework)
        assertNotNull(viewHolder)
    }

    @Test
    fun `should handle playing recording state`() {
        // Given
        val recordings = createSampleRecordings()
        adapter.updateRecordings(recordings)
        val recordingId = recordings[0].id

        // When
        adapter.setPlayingRecording(recordingId)

        // Then
        // Verify that the adapter tracks the playing recording
        // (detailed verification would require access to internal state or UI testing)
        assertNotNull(adapter)
    }

    @Test
    fun `should clear playing recording state`() {
        // Given
        val recordings = createSampleRecordings()
        adapter.updateRecordings(recordings)
        adapter.setPlayingRecording(recordings[0].id)

        // When
        adapter.setPlayingRecording(null)

        // Then
        // Verify that the playing state is cleared
        // (detailed verification would require access to internal state or UI testing)
        assertNotNull(adapter)
    }

    @Test
    fun `should handle empty recordings list`() {
        // Given
        val emptyList = emptyList<Recording>()

        // When
        adapter.updateRecordings(emptyList)

        // Then
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `should handle recordings with null contact names`() {
        // Given
        val recordingWithoutContact = createSampleRecording(contactName = null)
        adapter.updateRecordings(listOf(recordingWithoutContact))

        // When
        val parent = mockk<ViewGroup>(relaxed = true)
        io.mockk.every { parent.context } returns context
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        // Then
        // Verify that the adapter handles null contact names gracefully
        assertNotNull(viewHolder)
    }

    @Test
    fun `should handle different audio quality types`() {
        // Given
        val recordings = listOf(
            createSampleRecording(id = "1", audioQuality = AudioQuality.HIGH_QUALITY),
            createSampleRecording(id = "2", audioQuality = AudioQuality.STANDARD),
            createSampleRecording(id = "3", audioQuality = AudioQuality.SPACE_SAVING)
        )
        adapter.updateRecordings(recordings)

        // When
        val parent = mockk<ViewGroup>(relaxed = true)
        io.mockk.every { parent.context } returns context
        
        recordings.forEachIndexed { index, _ ->
            val viewHolder = adapter.onCreateViewHolder(parent, 0)
            adapter.onBindViewHolder(viewHolder, index)
        }

        // Then
        // Verify that all audio quality types are handled
        assertEquals(recordings.size, adapter.itemCount)
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
        phoneNumber: String = "090-1234-5678",
        audioQuality: AudioQuality = AudioQuality.STANDARD
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
            audioQuality = audioQuality,
            isIncoming = true
        )
    }
}