package com.callrecorder.pixel.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.callrecorder.pixel.R
import com.callrecorder.pixel.data.model.Recording
import com.callrecorder.pixel.data.repository.RecordingRepository
import com.callrecorder.pixel.ui.adapter.RecordingListAdapter
import com.callrecorder.pixel.ui.dialog.RecordingDetailsDialog
import com.callrecorder.pixel.ui.media.RecordingMediaPlayer
import kotlinx.coroutines.launch
import java.io.File

/**
 * Activity for displaying the list of recorded calls
 * Provides functionality to view, play, delete, and share recordings
 */
class RecordingListActivity : AppCompatActivity(), 
    RecordingListAdapter.OnRecordingClickListener,
    RecordingDetailsDialog.RecordingDetailsListener {
    
    companion object {
        private const val TAG = "RecordingListActivity"
    }
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: Toolbar
    
    private lateinit var recordingRepository: RecordingRepository
    private lateinit var adapter: RecordingListAdapter
    
    private val mediaPlayer = RecordingMediaPlayer()
    private var currentRecordings: List<Recording> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording_list)
        
        initializeViews()
        setupToolbar()
        setupRecyclerView()
        initializeRepository()
        loadRecordings()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_recording_list, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadRecordings()
                true
            }
            R.id.action_sort -> {
                showSortOptions()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewRecordings)
        emptyView = findViewById(R.id.textViewEmpty)
        progressBar = findViewById(R.id.progressBar)
        toolbar = findViewById(R.id.toolbar)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = RecordingListAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun initializeRepository() {
        recordingRepository = RecordingRepository.getInstance(this)
    }
    
    private fun loadRecordings() {
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val recordings = recordingRepository.getAllRecordings()
                // Sort recordings by date (newest first)
                val sortedRecordings = recordings.sortedByDescending { it.recordingDate }
                
                runOnUiThread {
                    showLoading(false)
                    updateRecordingsList(sortedRecordings)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recordings", e)
                runOnUiThread {
                    showLoading(false)
                    showError("録音リストの読み込みに失敗しました")
                }
            }
        }
    }
    
    private fun updateRecordingsList(recordings: List<Recording>) {
        currentRecordings = recordings
        if (recordings.isEmpty()) {
            showEmptyState(true)
        } else {
            showEmptyState(false)
            adapter.updateRecordings(recordings)
        }
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        emptyView.visibility = View.GONE
    }
    
    private fun showEmptyState(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    // RecordingListAdapter.OnRecordingClickListener implementation
    override fun onRecordingClick(recording: Recording) {
        showRecordingDetails(recording)
    }
    
    override fun onPlayClick(recording: Recording) {
        playRecording(recording)
    }
    
    override fun onDeleteClick(recording: Recording) {
        showDeleteConfirmationDialog(recording)
    }
    
    override fun onShareClick(recording: Recording) {
        shareRecording(recording)
    }
    
    // RecordingDetailsDialog.RecordingDetailsListener implementation
    override fun onDeleteRequested(recording: Recording) {
        deleteRecording(recording)
    }
    
    override fun onShareRequested(recording: Recording) {
        shareRecording(recording)
    }
    
    private fun showRecordingDetails(recording: Recording) {
        lifecycleScope.launch {
            try {
                val recordingFile = recordingRepository.getRecordingFile(recording.id)
                if (recordingFile != null && recordingFile.exists()) {
                    val dialog = RecordingDetailsDialog(this@RecordingListActivity, recording, recordingFile, this@RecordingListActivity)
                    dialog.show()
                } else {
                    showError("録音ファイルが見つかりません")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing recording details", e)
                showError("録音詳細の表示に失敗しました")
            }
        }
    }
    
    private fun playRecording(recording: Recording) {
        lifecycleScope.launch {
            try {
                val recordingFile = recordingRepository.getRecordingFile(recording.id)
                if (recordingFile != null && recordingFile.exists()) {
                    mediaPlayer.playRecording(recording, recordingFile)
                    adapter.setPlayingRecording(recording.id)
                    showMessage("再生開始: ${recording.getDisplayName()}")
                } else {
                    showError("録音ファイルが見つかりません")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing recording", e)
                showError("再生に失敗しました")
            }
        }
    }
    
    private fun showDeleteConfirmationDialog(recording: Recording) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_recording))
            .setMessage(getString(R.string.delete_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteRecording(recording)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun deleteRecording(recording: Recording) {
        lifecycleScope.launch {
            try {
                val success = recordingRepository.deleteRecording(recording.id)
                runOnUiThread {
                    if (success) {
                        showMessage("録音を削除しました")
                        // Reload the list
                        loadRecordings()
                    } else {
                        showError("削除に失敗しました")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting recording", e)
                runOnUiThread {
                    showError("削除中にエラーが発生しました")
                }
            }
        }
    }
    
    private fun shareRecording(recording: Recording) {
        lifecycleScope.launch {
            try {
                val recordingFile = recordingRepository.getRecordingFile(recording.id)
                if (recordingFile != null && recordingFile.exists()) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@RecordingListActivity,
                        "${packageName}.fileprovider",
                        recordingFile
                    )
                    
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "通話録音: ${recording.getDisplayName()}")
                        putExtra(Intent.EXTRA_TEXT, "録音日時: ${recording.getFormattedDate()}\n時間: ${recording.getFormattedDuration()}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_recording)))
                } else {
                    showError("録音ファイルが見つかりません")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing recording", e)
                showError("共有に失敗しました")
            }
        }
    }
    
    private fun showSortOptions() {
        val options = arrayOf("日付順（新しい順）", "日付順（古い順）", "時間順（長い順）", "時間順（短い順）", "連絡先順")
        
        AlertDialog.Builder(this)
            .setTitle("並び替え")
            .setItems(options) { _, which ->
                sortRecordings(which)
            }
            .show()
    }
    
    private fun sortRecordings(sortType: Int) {
        val sortedRecordings = when (sortType) {
            0 -> currentRecordings.sortedByDescending { it.recordingDate }
            1 -> currentRecordings.sortedBy { it.recordingDate }
            2 -> currentRecordings.sortedByDescending { it.duration }
            3 -> currentRecordings.sortedBy { it.duration }
            4 -> currentRecordings.sortedBy { it.getDisplayName() }
            else -> currentRecordings
        }
        
        updateRecordingsList(sortedRecordings)
    }
    
    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}