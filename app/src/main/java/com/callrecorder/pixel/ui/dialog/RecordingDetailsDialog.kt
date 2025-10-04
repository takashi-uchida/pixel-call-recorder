package com.callrecorder.pixel.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.callrecorder.pixel.R
import com.callrecorder.pixel.data.model.Recording
import com.callrecorder.pixel.ui.media.RecordingMediaPlayer
import java.io.File

/**
 * Dialog for displaying recording details and playback controls
 */
class RecordingDetailsDialog(
    private val context: Context,
    private val recording: Recording,
    private val recordingFile: File,
    private val listener: RecordingDetailsListener
) : RecordingMediaPlayer.PlaybackListener {
    
    interface RecordingDetailsListener {
        fun onDeleteRequested(recording: Recording)
        fun onShareRequested(recording: Recording)
    }
    
    private var dialog: Dialog? = null
    private val mediaPlayer = RecordingMediaPlayer()
    private val handler = Handler(Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null
    
    // Views
    private lateinit var textViewContactName: TextView
    private lateinit var textViewPhoneNumber: TextView
    private lateinit var textViewDateTime: TextView
    private lateinit var textViewDirection: TextView
    private lateinit var textViewDuration: TextView
    private lateinit var textViewFileSize: TextView
    private lateinit var textViewAudioQuality: TextView
    private lateinit var seekBarProgress: SeekBar
    private lateinit var textViewCurrentTime: TextView
    private lateinit var textViewTotalTime: TextView
    private lateinit var buttonPlay: Button
    private lateinit var buttonDelete: Button
    private lateinit var buttonShare: Button
    
    private var isPlaying = false
    
    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_recording_details, null)
        initializeViews(view)
        setupViews()
        setupListeners()
        
        dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .setOnDismissListener {
                cleanup()
            }
            .create()
        
        dialog?.show()
        
        mediaPlayer.setPlaybackListener(this)
    }
    
    private fun initializeViews(view: android.view.View) {
        textViewContactName = view.findViewById(R.id.textViewContactName)
        textViewPhoneNumber = view.findViewById(R.id.textViewPhoneNumber)
        textViewDateTime = view.findViewById(R.id.textViewDateTime)
        textViewDirection = view.findViewById(R.id.textViewDirection)
        textViewDuration = view.findViewById(R.id.textViewDuration)
        textViewFileSize = view.findViewById(R.id.textViewFileSize)
        textViewAudioQuality = view.findViewById(R.id.textViewAudioQuality)
        seekBarProgress = view.findViewById(R.id.seekBarProgress)
        textViewCurrentTime = view.findViewById(R.id.textViewCurrentTime)
        textViewTotalTime = view.findViewById(R.id.textViewTotalTime)
        buttonPlay = view.findViewById(R.id.buttonPlay)
        buttonDelete = view.findViewById(R.id.buttonDelete)
        buttonShare = view.findViewById(R.id.buttonShare)
    }
    
    private fun setupViews() {
        textViewContactName.text = recording.getDisplayName()
        textViewPhoneNumber.text = recording.phoneNumber
        textViewDateTime.text = recording.getFormattedDate()
        textViewDirection.text = recording.getCallDirection()
        textViewDuration.text = recording.getFormattedDuration()
        textViewFileSize.text = recording.getFormattedFileSize()
        textViewAudioQuality.text = when (recording.audioQuality) {
            com.callrecorder.pixel.data.model.AudioQuality.HIGH_QUALITY -> "高品質"
            com.callrecorder.pixel.data.model.AudioQuality.STANDARD -> "標準"
            com.callrecorder.pixel.data.model.AudioQuality.SPACE_SAVING -> "省容量"
        }
        
        textViewTotalTime.text = recording.getFormattedDuration()
        textViewCurrentTime.text = "00:00"
        
        seekBarProgress.max = (recording.duration / 1000).toInt()
        seekBarProgress.progress = 0
    }
    
    private fun setupListeners() {
        buttonPlay.setOnClickListener {
            if (isPlaying) {
                pausePlayback()
            } else {
                startPlayback()
            }
        }
        
        buttonDelete.setOnClickListener {
            showDeleteConfirmation()
        }
        
        buttonShare.setOnClickListener {
            listener.onShareRequested(recording)
            dismiss()
        }
        
        seekBarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress * 1000)
                    updateCurrentTimeDisplay(progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun startPlayback() {
        mediaPlayer.playRecording(recording, recordingFile)
    }
    
    private fun pausePlayback() {
        mediaPlayer.pause()
        isPlaying = false
        buttonPlay.text = "再生"
        stopProgressUpdates()
    }
    
    private fun stopPlayback() {
        mediaPlayer.stop()
        isPlaying = false
        buttonPlay.text = "再生"
        seekBarProgress.progress = 0
        textViewCurrentTime.text = "00:00"
        stopProgressUpdates()
    }
    
    private fun showDeleteConfirmation() {
        AlertDialog.Builder(context)
            .setTitle("録音を削除")
            .setMessage("この録音を削除しますか？")
            .setPositiveButton("削除") { _, _ ->
                listener.onDeleteRequested(recording)
                dismiss()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }
    
    private fun startProgressUpdates() {
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (isPlaying && mediaPlayer.isPlaying()) {
                    val currentPosition = mediaPlayer.getCurrentPosition() / 1000
                    seekBarProgress.progress = currentPosition
                    updateCurrentTimeDisplay(currentPosition)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(progressUpdateRunnable!!)
    }
    
    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let { handler.removeCallbacks(it) }
        progressUpdateRunnable = null
    }
    
    private fun updateCurrentTimeDisplay(seconds: Int) {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        textViewCurrentTime.text = String.format("%02d:%02d", minutes, remainingSeconds)
    }
    
    private fun dismiss() {
        dialog?.dismiss()
    }
    
    private fun cleanup() {
        stopProgressUpdates()
        mediaPlayer.release()
        dialog = null
    }
    
    // RecordingMediaPlayer.PlaybackListener implementation
    override fun onPlaybackStarted(recording: Recording) {
        isPlaying = true
        buttonPlay.text = "一時停止"
        startProgressUpdates()
    }
    
    override fun onPlaybackCompleted(recording: Recording) {
        isPlaying = false
        buttonPlay.text = "再生"
        seekBarProgress.progress = 0
        textViewCurrentTime.text = "00:00"
        stopProgressUpdates()
    }
    
    override fun onPlaybackError(recording: Recording, error: String) {
        isPlaying = false
        buttonPlay.text = "再生"
        stopProgressUpdates()
        
        AlertDialog.Builder(context)
            .setTitle("再生エラー")
            .setMessage(error)
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onPlaybackProgress(recording: Recording, currentPosition: Int, duration: Int) {
        // This method can be used for more frequent progress updates if needed
    }
}