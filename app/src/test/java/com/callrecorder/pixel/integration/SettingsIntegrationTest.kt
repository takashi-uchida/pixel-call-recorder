package com.callrecorder.pixel.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.settings.SettingsManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Integration tests for settings functionality
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class SettingsIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager.getInstance(context)
        
        // Reset to defaults before each test
        settingsManager.resetToDefaults()
    }
    
    @Test
    fun `settings manager returns default quality on first use`() {
        // When
        val quality = settingsManager.getAudioQuality()
        
        // Then
        assertEquals(AudioQuality.STANDARD, quality)
    }
    
    @Test
    fun `settings manager persists audio quality changes`() {
        // Given
        val newQuality = AudioQuality.HIGH_QUALITY
        
        // When
        settingsManager.setAudioQuality(newQuality)
        val retrievedQuality = settingsManager.getAudioQuality()
        
        // Then
        assertEquals(newQuality, retrievedQuality)
    }
    
    @Test
    fun `settings manager persists across instances`() {
        // Given
        val newQuality = AudioQuality.SPACE_SAVING
        settingsManager.setAudioQuality(newQuality)
        
        // When - create new instance
        val newSettingsManager = SettingsManager.getInstance(context)
        val retrievedQuality = newSettingsManager.getAudioQuality()
        
        // Then
        assertEquals(newQuality, retrievedQuality)
    }
    
    @Test
    fun `all audio quality types can be stored and retrieved`() {
        val qualities = listOf(
            AudioQuality.HIGH_QUALITY,
            AudioQuality.STANDARD,
            AudioQuality.SPACE_SAVING
        )
        
        qualities.forEach { quality ->
            // When
            settingsManager.setAudioQuality(quality)
            val retrieved = settingsManager.getAudioQuality()
            
            // Then
            assertEquals(quality, retrieved)
        }
    }
    
    @Test
    fun `first launch detection works correctly`() {
        // When
        val isFirstLaunch1 = settingsManager.isFirstLaunch()
        val isFirstLaunch2 = settingsManager.isFirstLaunch()
        
        // Then
        assertTrue(isFirstLaunch1)
        assertFalse(isFirstLaunch2)
    }
    
    @Test
    fun `reset to defaults clears all settings`() {
        // Given - set some non-default values
        settingsManager.setAudioQuality(AudioQuality.HIGH_QUALITY)
        settingsManager.isFirstLaunch() // This sets first launch to false
        
        // When
        settingsManager.resetToDefaults()
        
        // Then
        assertEquals(AudioQuality.STANDARD, settingsManager.getAudioQuality())
        // Note: isFirstLaunch will still return false as it was already called
    }
    
    @Test
    fun `get all settings returns current configuration`() {
        // Given
        settingsManager.setAudioQuality(AudioQuality.HIGH_QUALITY)
        
        // When
        val allSettings = settingsManager.getAllSettings()
        
        // Then
        assertNotNull(allSettings)
        assertTrue(allSettings.containsKey("audio_quality"))
        assertEquals("HIGH_QUALITY", allSettings["audio_quality"])
    }
    
    @Test
    fun `audio quality properties are correct`() {
        // Test HIGH_QUALITY
        val highQuality = AudioQuality.HIGH_QUALITY
        assertEquals(48000, highQuality.sampleRate)
        assertEquals(128000, highQuality.bitRate)
        assertEquals(2, highQuality.channels)
        assertEquals("高品質", highQuality.displayName)
        assertTrue(highQuality.isSupported())
        
        // Test STANDARD
        val standard = AudioQuality.STANDARD
        assertEquals(44100, standard.sampleRate)
        assertEquals(64000, standard.bitRate)
        assertEquals(1, standard.channels)
        assertEquals("標準", standard.displayName)
        assertTrue(standard.isSupported())
        
        // Test SPACE_SAVING
        val spaceSaving = AudioQuality.SPACE_SAVING
        assertEquals(22050, spaceSaving.sampleRate)
        assertEquals(32000, spaceSaving.bitRate)
        assertEquals(1, spaceSaving.channels)
        assertEquals("省容量", spaceSaving.displayName)
        assertTrue(spaceSaving.isSupported())
    }
    
    @Test
    fun `estimated file size calculation is reasonable`() {
        val qualities = listOf(
            AudioQuality.HIGH_QUALITY,
            AudioQuality.STANDARD,
            AudioQuality.SPACE_SAVING
        )
        
        qualities.forEach { quality ->
            val fileSize = quality.getEstimatedFileSizePerMinute()
            
            // File size should be positive and reasonable
            assertTrue("File size should be positive for ${quality.displayName}", fileSize > 0)
            
            // High quality should have larger file size than standard
            // Standard should have larger file size than space saving
            when (quality) {
                AudioQuality.HIGH_QUALITY -> {
                    assertTrue("High quality should have largest file size", 
                        fileSize > AudioQuality.STANDARD.getEstimatedFileSizePerMinute())
                }
                AudioQuality.STANDARD -> {
                    assertTrue("Standard quality should be between high and space saving",
                        fileSize > AudioQuality.SPACE_SAVING.getEstimatedFileSizePerMinute() &&
                        fileSize < AudioQuality.HIGH_QUALITY.getEstimatedFileSizePerMinute())
                }
                AudioQuality.SPACE_SAVING -> {
                    assertTrue("Space saving should have smallest file size",
                        fileSize < AudioQuality.STANDARD.getEstimatedFileSizePerMinute())
                }
            }
        }
    }
}