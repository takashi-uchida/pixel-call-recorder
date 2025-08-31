package com.callrecorder.pixel.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.callrecorder.pixel.R
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.settings.SettingsManager

/**
 * Settings activity for configuring recording quality and other app preferences
 */
class SettingsActivity : AppCompatActivity() {
    
    private lateinit var toolbar: Toolbar
    private lateinit var rgAudioQuality: RadioGroup
    private lateinit var rbHighQuality: RadioButton
    private lateinit var rbStandard: RadioButton
    private lateinit var rbSpaceSaving: RadioButton
    private lateinit var tvQualityDescription: TextView
    
    private lateinit var settingsManager: SettingsManager
    private val settingsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // Handle settings changes if needed
        when (key) {
            "audio_quality" -> {
                // Settings changed, could notify other components if needed
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        initializeViews()
        setupToolbar()
        initializeSettings()
        setupQualitySelection()
        loadCurrentSettings()
    }
    
    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        rgAudioQuality = findViewById(R.id.rgAudioQuality)
        rbHighQuality = findViewById(R.id.rbHighQuality)
        rbStandard = findViewById(R.id.rbStandard)
        rbSpaceSaving = findViewById(R.id.rbSpaceSaving)
        tvQualityDescription = findViewById(R.id.tvQualityDescription)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.settings)
        }
    }
    
    private fun initializeSettings() {
        settingsManager = SettingsManager.getInstance(this)
        settingsManager.registerOnSettingsChangeListener(settingsChangeListener)
    }
    
    private fun setupQualitySelection() {
        rgAudioQuality.setOnCheckedChangeListener { _, checkedId ->
            val selectedQuality = when (checkedId) {
                R.id.rbHighQuality -> AudioQuality.HIGH_QUALITY
                R.id.rbStandard -> AudioQuality.STANDARD
                R.id.rbSpaceSaving -> AudioQuality.SPACE_SAVING
                else -> AudioQuality.STANDARD
            }
            
            updateQualityDescription(selectedQuality)
            settingsManager.setAudioQuality(selectedQuality)
        }
    }
    
    private fun loadCurrentSettings() {
        val currentQuality = settingsManager.getAudioQuality()
        
        // Set the appropriate radio button
        when (currentQuality) {
            AudioQuality.HIGH_QUALITY -> rbHighQuality.isChecked = true
            AudioQuality.STANDARD -> rbStandard.isChecked = true
            AudioQuality.SPACE_SAVING -> rbSpaceSaving.isChecked = true
        }
        
        updateQualityDescription(currentQuality)
    }
    
    private fun updateQualityDescription(quality: AudioQuality) {
        val description = buildString {
            append("サンプルレート: ${quality.sampleRate}Hz\n")
            append("ビットレート: ${quality.bitRate}bps\n")
            append("チャンネル: ${if (quality.channels == 2) "ステレオ" else "モノラル"}\n")
            append("推定ファイルサイズ: ${formatFileSize(quality.getEstimatedFileSizePerMinute())}/分")
        }
        
        tvQualityDescription.text = description
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        settingsManager.unregisterOnSettingsChangeListener(settingsChangeListener)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    

}