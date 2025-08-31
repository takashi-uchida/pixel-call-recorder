package com.callrecorder.pixel.settings

import android.content.Context
import android.content.SharedPreferences
import com.callrecorder.pixel.data.model.AudioQuality
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SettingsManager
 */
class SettingsManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var settingsManager: SettingsManager
    
    @Before
    fun setup() {
        mockContext = mockk()
        mockSharedPreferences = mockk()
        mockEditor = mockk()
        
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSharedPreferences("call_recorder_settings", Context.MODE_PRIVATE) } returns mockSharedPreferences
        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit
        every { mockEditor.clear() } returns mockEditor
        
        // Create SettingsManager instance using reflection to bypass singleton
        val constructor = SettingsManager::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        settingsManager = constructor.newInstance(mockContext)
    }
    
    @Test
    fun `getAudioQuality returns default when no preference set`() {
        // Given
        every { mockSharedPreferences.getString("audio_quality", "STANDARD") } returns "STANDARD"
        
        // When
        val result = settingsManager.getAudioQuality()
        
        // Then
        assertEquals(AudioQuality.STANDARD, result)
    }
    
    @Test
    fun `getAudioQuality returns stored preference`() {
        // Given
        every { mockSharedPreferences.getString("audio_quality", "STANDARD") } returns "HIGH_QUALITY"
        
        // When
        val result = settingsManager.getAudioQuality()
        
        // Then
        assertEquals(AudioQuality.HIGH_QUALITY, result)
    }
    
    @Test
    fun `getAudioQuality returns default when invalid preference stored`() {
        // Given
        every { mockSharedPreferences.getString("audio_quality", "STANDARD") } returns "INVALID_QUALITY"
        
        // When
        val result = settingsManager.getAudioQuality()
        
        // Then
        assertEquals(AudioQuality.STANDARD, result)
        verify { mockEditor.putString("audio_quality", "STANDARD") }
        verify { mockEditor.apply() }
    }
    
    @Test
    fun `setAudioQuality stores preference correctly`() {
        // Given
        val quality = AudioQuality.HIGH_QUALITY
        
        // When
        settingsManager.setAudioQuality(quality)
        
        // Then
        verify { mockEditor.putString("audio_quality", "HIGH_QUALITY") }
        verify { mockEditor.apply() }
    }
    
    @Test
    fun `setAudioQuality stores all quality types correctly`() {
        // Test all quality types
        val qualities = listOf(
            AudioQuality.HIGH_QUALITY,
            AudioQuality.STANDARD,
            AudioQuality.SPACE_SAVING
        )
        
        qualities.forEach { quality ->
            // When
            settingsManager.setAudioQuality(quality)
            
            // Then
            verify { mockEditor.putString("audio_quality", quality.name) }
        }
        
        verify(exactly = qualities.size) { mockEditor.apply() }
    }
    
    @Test
    fun `isFirstLaunch returns true on first call and false afterwards`() {
        // Given
        every { mockSharedPreferences.getBoolean("is_first_launch", true) } returnsMany listOf(true, false)
        
        // When
        val firstCall = settingsManager.isFirstLaunch()
        val secondCall = settingsManager.isFirstLaunch()
        
        // Then
        assertTrue(firstCall)
        assertFalse(secondCall)
        verify { mockEditor.putBoolean("is_first_launch", false) }
        verify { mockEditor.apply() }
    }
    
    @Test
    fun `resetToDefaults clears preferences and sets defaults`() {
        // When
        settingsManager.resetToDefaults()
        
        // Then
        verify { mockEditor.clear() }
        verify { mockEditor.putString("audio_quality", "STANDARD") }
        verify { mockEditor.apply() }
    }
    
    @Test
    fun `getAllSettings returns all preferences`() {
        // Given
        val expectedSettings = mapOf(
            "audio_quality" to "HIGH_QUALITY",
            "is_first_launch" to false
        )
        every { mockSharedPreferences.all } returns expectedSettings
        
        // When
        val result = settingsManager.getAllSettings()
        
        // Then
        assertEquals(expectedSettings, result)
    }
    
    @Test
    fun `registerOnSettingsChangeListener registers listener correctly`() {
        // Given
        val listener = mockk<SharedPreferences.OnSharedPreferenceChangeListener>()
        every { mockSharedPreferences.registerOnSharedPreferenceChangeListener(listener) } returns Unit
        
        // When
        settingsManager.registerOnSettingsChangeListener(listener)
        
        // Then
        verify { mockSharedPreferences.registerOnSharedPreferenceChangeListener(listener) }
    }
    
    @Test
    fun `unregisterOnSettingsChangeListener unregisters listener correctly`() {
        // Given
        val listener = mockk<SharedPreferences.OnSharedPreferenceChangeListener>()
        every { mockSharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) } returns Unit
        
        // When
        settingsManager.unregisterOnSettingsChangeListener(listener)
        
        // Then
        verify { mockSharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}