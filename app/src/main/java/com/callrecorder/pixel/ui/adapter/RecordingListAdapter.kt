package com.callrecorder.pixel.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.callrecorder.pixel.R
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.Recording

/**
 * RecyclerView adapter for displaying the list of recordings
 */
class RecordingListAdapter(
    private val clickListener: OnRecordingClickListener
) : RecyclerView.Adapter<RecordingListAdapter.RecordingViewHolder>() {
    
    private var recordings: List<Recording> = emptyList()
    private var playingRecordingId: String? = null
    
    interface OnRecordingClickListener {
        fun onRecordingClick(recording: Recording)
        fun onPlayClick(recording: Recording)
        fun onDeleteClick(recording: Recording)
        fun onShareClick(recording: Recording)
    }
    
    fun updateRecordings(newRecordings: List<Recording>) {
        recordings = newRecordings
        notifyDataSetChanged()
    }
    
    fun setPlayingRecording(recordingId: String?) {
        val oldPlayingId = playingRecordingId
        playingRecordingId = recordingId
        
        // Update the old playing item
        oldPlayingId?.let { id ->
            val oldIndex = recordings.indexOfFirst { it.id == id }
            if (oldIndex != -1) {
                notifyItemChanged(oldIndex)
            }
        }
        
        // Update the new playing item
        recordingId?.let { id ->
            val newIndex = recordings.indexOfFirst { it.id == id }
            if (newIndex != -1) {
                notifyItemChanged(newIndex)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return RecordingViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        val recording = recordings[position]
        holder.bind(recording, recording.id == playingRecordingId)
    }
    
    override fun getItemCount(): Int = recordings.size
    
    inner class RecordingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageViewCallDirection: ImageView = itemView.findViewById(R.id.imageViewCallDirection)
        private val textViewContactName: TextView = itemView.findViewById(R.id.textViewContactName)
        private val textViewPhoneNumber: TextView = itemView.findViewById(R.id.textViewPhoneNumber)
        private val textViewDateTime: TextView = itemView.findViewById(R.id.textViewDateTime)
        private val textViewDuration: TextView = itemView.findViewById(R.id.textViewDuration)
        private val textViewFileSize: TextView = itemView.findViewById(R.id.textViewFileSize)
        private val textViewAudioQuality: TextView = itemView.findViewById(R.id.textViewAudioQuality)
        
        fun bind(recording: Recording, isPlaying: Boolean) {
            // Set call direction icon
            imageViewCallDirection.setImageResource(
                if (recording.isIncoming) R.drawable.ic_permission_granted 
                else R.drawable.ic_permission_denied
            )
            
            // Set contact name or phone number
            textViewContactName.text = recording.getDisplayName()
            textViewPhoneNumber.text = recording.phoneNumber
            
            // Set date and time
            textViewDateTime.text = recording.getFormattedDate()
            
            // Set duration
            textViewDuration.text = recording.getFormattedDuration()
            
            // Set file size
            textViewFileSize.text = recording.getFormattedFileSize()
            
            // Set audio quality
            textViewAudioQuality.text = when (recording.audioQuality) {
                AudioQuality.HIGH_QUALITY -> "高品質"
                AudioQuality.STANDARD -> "標準"
                AudioQuality.SPACE_SAVING -> "省容量"
            }
            
            // Highlight if currently playing
            if (isPlaying) {
                itemView.alpha = 0.7f
                textViewContactName.text = "${recording.getDisplayName()} (再生中)"
            } else {
                itemView.alpha = 1.0f
            }
            
            // Set click listeners
            itemView.setOnClickListener {
                clickListener.onRecordingClick(recording)
            }
            
            itemView.setOnLongClickListener {
                showContextMenu(recording)
                true
            }
        }
        
        private fun showContextMenu(recording: Recording) {
            val context = itemView.context
            val options = arrayOf("再生", "削除", "共有")
            
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(recording.getDisplayName())
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> clickListener.onPlayClick(recording)
                        1 -> clickListener.onDeleteClick(recording)
                        2 -> clickListener.onShareClick(recording)
                    }
                }
                .show()
        }
    }
}