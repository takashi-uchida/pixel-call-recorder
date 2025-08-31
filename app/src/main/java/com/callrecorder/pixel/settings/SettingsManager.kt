package com.callrecorder.pixel.settings

import android.content.Context
import android.content.SharedPreferences
import com.callrecorder.pixel.data.model.AudioQuality

/**
 * Manager class for handling app settings and preferences
 * Provides centralized access to all app settings
 */
class SettingsManager private constructor(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "call_recorder_settings"
        private const val KEY_AUDIO_QUALITY = "audio_quality"
        private const val DEFAULT_AUDIO_QUALITY = "STANDARD"
        
        @Volatile
        private var INSTANCE: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Gets the current audio quality setting
     */
    fun getAudioQuality(): AudioQuality {
        val qualityName = sharedPreferences.getString(KEY_AUDIO_QUALITY, DEFAULT_AUDIO_QUALITY)
        return try {
            AudioQuality.valueOf(qualityName ?: DEFAULT_AUDIO_QUALITY)
        } catch (e: IllegalArgumentException) {
            // If invalid value is stored, return default and fix it
            setAudioQuality(AudioQuality.STANDARD)
            AudioQuality.STANDARD
        }
    }
    
    /**
     * Sets the audio quality setting
     */
    fun setAudioQuality(quality: AudioQuality) {
        sharedPreferences.edit()
            .putString(KEY_AUDIO_QUALITY, quality.name)
            .apply()
    }
    
    /**
     * Registers a listener for settings changes
     */
    fun registerOnSettingsChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }
    
    /**
     * Unregisters a settings change listener
     */
    fun unregisterOnSettingsChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
    
    /**
     * Resets all settings to default values
     */
    fun resetToDefaults() {
        sharedPreferences.edit()
            .clear()
            .putString(KEY_AUDIO_QUALITY, DEFAULT_AUDIO_QUALITY)
            .apply()
    }
    
    /**
     * Checks if this is the first time the app is launched
     */
    fun isFirstLaunch(): Boolean {
        val isFirst = sharedPreferences.getBoolean("is_first_launch", true)
        if (isFirst) {
            sharedPreferences.edit()
                .putBoolean("is_first_launch", false)
                .apply()
        }
        return isFirst
    }
    
    /**
     * Gets all current settings as a map for debugging/export purposes
     */
    fun getAllSettings(): Map<String, Any?> {
        return sharedPreferences.all
    }
}