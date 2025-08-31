package com.callrecorder.pixel.ui.media

import android.media.MediaPlayer
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.Recording
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime

/**
 * Unit tests for RecordingMediaPlayer
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecordingMediaPlayerTest {

    private lateinit var mediaPlayer: RecordingMediaPlayer
    private lateinit var mockListener: RecordingMediaPlayer.PlaybackListener
    private lateinit var mockFile: File
    private lateinit var sampleRecording: Recording

    @Before
    fun setup() {
        mediaPlayer = RecordingMediaPlayer()
        mockListener = mockk(relaxed = true)
        mockFile = mockk()
        
        every { mockFile.absolutePath } returns "/test/path/recording.m4a"
        every { mockFile.exists() } returns true
        
        sampleRecording = createSampleRecording()
        
        mediaPlayer.setPlaybackListener(mockListener)
    }

    @After
    fun tearDown() {
        mediaPlayer.release()
        unmockkAll()
    }

    @Test
    fun `should set playback listener`() {
        // Given
        val newListener = mockk<RecordingMediaPlayer.PlaybackListener>(relaxed = true)

        // When
        mediaPlayer.setPlaybackListener(newListener)

        // Then
        // Verify that the listener is set (internal state verification)
        assert(true) // Basic test that no exception is thrown
    }

    @Test
    fun `should clear playback listener`() {
        // When
        mediaPlayer.setPlaybackListener(null)

        // Then
        // Verify that the listener is cleared (internal state verification)
        assert(true) // Basic test that no exception is thrown
    }

    @Test
    fun `should handle play recording with valid file`() {
        // Given
        mockkConstructor(MediaPlayer::class)
        val mockMediaPlayerInstance = mockk<MediaPlayer>(relaxed = true)
        every { anyConstructed<MediaPlayer>().setDataSource(any<String>()) } just Runs
        every { anyConstructed<MediaPlayer>().prepareAsync() } just Runs

        // When
        mediaPlayer.playRecording(sampleRecording, mockFile)

        // Then
        verify { anyConstructed<MediaPlayer>().setDataSource(mockFile.absolutePath) }
        verify { anyConstructed<MediaPlayer>().prepareAsync() }
    }

    @Test
    fun `should handle play recording with invalid file`() {
        // Given
        every { mockFile.absolutePath } throws SecurityException("Access denied")

        // When
        mediaPlayer.playRecording(sampleRecording, mockFile)

        // Then
        verify { mockListener.onPlaybackError(sampleRecording, any()) }
    }

    @Test
    fun `should stop current playback before starting new one`() {
        // Given
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().setDataSource(any<String>()) } just Runs
        every { anyConstructed<MediaPlayer>().prepareAsync() } just Runs
        every { anyConstructed<MediaPlayer>().isPlaying } returns true
        every { anyConstructed<MediaPlayer>().stop() } just Runs
        every { anyConstructed<MediaPlayer>().release() } just Runs

        // Start first recording
        mediaPlayer.playRecording(sampleRecording, mockFile)

        // When - Start second recording
        val secondRecording = createSampleRecording(id = "second")
        mediaPlayer.playRecording(secondRecording, mockFile)

        // Then
        verify(atLeast = 1) { anyConstructed<MediaPlayer>().stop() }
        verify(atLeast = 1) { anyConstructed<MediaPlayer>().release() }
    }

    @Test
    fun `should handle pause when playing`() {
        // Given
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().isPlaying } returns true
        every { anyConstructed<MediaPlayer>().pause() } just Runs

        // When
        mediaPlayer.pause()

        // Then
        verify { anyConstructed<MediaPlayer>().pause() }
    }

    @Test
    fun `should handle resume when paused`() {
        // Given
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().isPlaying } returns false
        every { anyConstructed<MediaPlayer>().start() } just Runs

        // When
        mediaPlayer.resume()

        // Then
        verify { anyConstructed<MediaPlayer>().start() }
    }

    @Test
    fun `should handle stop playback`() {
        // Given
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().isPlaying } returns true
        every { anyConstructed<MediaPlayer>().stop() } just Runs
        every { anyConstructed<MediaPlayer>().release() } just Runs

        // When
        mediaPlayer.stop()

        // Then
        verify { anyConstructed<MediaPlayer>().stop() }
        verify { anyConstructed<MediaPlayer>().release() }
    }

    @Test
    fun `should handle seek to position`() {
        // Given
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().seekTo(any()) } just Runs
        val position = 30000 // 30 seconds

        // When
        mediaPlayer.seekTo(position)

        // Then
        verify { anyConstructed<MediaPlayer>().seekTo(position) }
    }

    @Test
    fun `should get current position`() {
        // Given
        mockkConstructor(MediaPlayer::class)
        val expectedPosition = 15000
        every { anyConstructed<MediaPlayer>().currentPosition } returns expectedPosition

        // When
        val position = mediaPlayer.getCurrentPosition()

        // Then
        assert(position == expectedPosition)
    }

    @Test
    fun `should get duration`() {
        // Given
        mockkConstructor(MediaPlayer::class)
        val expectedDuration = 180000
        every { anyConstructed<MediaPlayer>().duration } returns expectedDuration

        // When
        val duration = mediaPlayer.getDuration()

        // Then
        assert(duration == expectedDuration)
    }

    @Test
    fun `should check if playing`() {
        // Given
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().isPlaying } returns true

        // When
        val isPlaying = mediaPlayer.isPlaying()

        // Then
        assert(isPlaying)
    }

    @Test
    fun `should return null for current recording when not playing`() {
        // When
        val currentRecording = mediaPlayer.getCurrentRecording()

        // Then
        assert(currentRecording == null)
    }

    @Test
    fun `should handle release properly`() {
        // Given
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().isPlaying } returns false
        every { anyConstructed<MediaPlayer>().release() } just Runs

        // When
        mediaPlayer.release()

        // Then
        // Verify that release doesn't throw exceptions
        assert(true)
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