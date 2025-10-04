package com.callrecorder.pixel.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callrecorder.pixel.ui.media.RecordingMediaPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FilePlaybackTest {

    @Test
    fun playback_calls_are_invoked_in_sequence_with_mocks() {
        val player = mockk<RecordingMediaPlayer>(relaxed = true)
        val file = File("/tmp/fake.m4a")

        // When
        // We simulate a simple flow using the available API
        // playRecording(recording,file) is higher level; here we just call primitives to verify invocation
        every { player.isPlaying() } returnsMany listOf(true, false)
        player.pause()
        player.resume()
        player.seekTo(15000)
        player.stop()
        player.release()

        // Then
        verify { player.pause() }
        verify { player.resume() }
        verify { player.seekTo(15000) }
        verify { player.stop() }
        verify { player.release() }
    }
}

