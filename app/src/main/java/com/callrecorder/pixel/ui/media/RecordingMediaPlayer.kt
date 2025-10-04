package com.callrecorder.pixel.ui.media

import android.media.MediaPlayer
import android.util.Log
import com.callrecorder.pixel.data.model.Recording
import java.io.File

/**
 * Manages media playback for recordings
 */
class RecordingMediaPlayer {
    
    companion object {
        private const val TAG = "RecordingMediaPlayer"
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecording: Recording? = null
    private var playbackListener: PlaybackListener? = null
    
    interface PlaybackListener {
        fun onPlaybackStarted(recording: Recording)
        fun onPlaybackCompleted(recording: Recording)
        fun onPlaybackError(recording: Recording, error: String)
        fun onPlaybackProgress(recording: Recording, currentPosition: Int, duration: Int)
    }
    
    fun setPlaybackListener(listener: PlaybackListener?) {
        this.playbackListener = listener
    }
    
    fun playRecording(recording: Recording, file: File) {
        try {
            // Stop current playback if any
            stop()
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                
                setOnPreparedListener { player ->
                    player.start()
                    currentRecording = recording
                    playbackListener?.onPlaybackStarted(recording)
                    Log.d(TAG, "Started playing recording: ${recording.id}")
                }
                
                setOnCompletionListener {
                    currentRecording?.let { rec ->
                        playbackListener?.onPlaybackCompleted(rec)
                    }
                    currentRecording = null
                    Log.d(TAG, "Completed playing recording: ${recording.id}")
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    val errorMessage = "再生エラー (code: $what, extra: $extra)"
                    playbackListener?.onPlaybackError(recording, errorMessage)
                    release()
                    currentRecording = null
                    true
                }
                
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
            playbackListener?.onPlaybackError(recording, "再生開始エラー: ${e.message}")
        }
    }
    
    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                Log.d(TAG, "Paused playback")
            }
        }
    }
    
    fun resume() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                Log.d(TAG, "Resumed playback")
            }
        }
    }
    
    fun stop() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
                Log.d(TAG, "Stopped and released MediaPlayer")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping MediaPlayer", e)
            }
        }
        mediaPlayer = null
        currentRecording = null
    }
    
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }
    
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }
    
    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }
    
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
    
    fun getCurrentRecording(): Recording? {
        return currentRecording
    }
    
    fun release() {
        stop()
        playbackListener = null
    }
}