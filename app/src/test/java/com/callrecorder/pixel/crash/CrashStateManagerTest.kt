package com.callrecorder.pixel.crash

import android.content.Context
import android.content.SharedPreferences
import com.callrecorder.pixel.data.model.CallInfo
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CrashStateManagerTest {

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var crashStateManager: CrashStateManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        
        every { context.getSharedPreferences(any(), any()) } returns preferences
        every { preferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } just Runs

        val filesDir = mockk<File>(relaxed = true)
        every { context.filesDir } returns filesDir

        crashStateManager = CrashStateManager(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `saveRecordingState saves all recording information`() {
        // Given
        val callInfo = CallInfo(
            callId = "test-call-123",
            phoneNumber = "+1234567890",
            contactName = "Test Contact",
            isIncoming = true,
            startTime = LocalDateTime.now()
        )
        val recordingFilePath = "/path/to/recording.mp3"
        val startTime = System.currentTimeMillis()

        // When
        crashStateManager.saveRecordingState(
            isRecording = true,
            callInfo = callInfo,
            recordingFilePath = recordingFilePath,
            startTime = startTime
        )

        // Then
        verify { editor.putBoolean("was_recording", true) }
        verify { editor.putLong("recording_start_time", startTime) }
        verify { editor.putString("recording_file_path", recordingFilePath) }
        verify { editor.putString("call_info_json", any()) }
        verify { editor.apply() }
    }

    @Test
    fun `saveRecordingState handles null values`() {
        // When
        crashStateManager.saveRecordingState(
            isRecording = false,
            callInfo = null,
            recordingFilePath = null,
            startTime = null
        )

        // Then
        verify { editor.putBoolean("was_recording", false) }
        verify { editor.putLong("recording_start_time", 0L) }
        verify { editor.putString("recording_file_path", null) }
        verify { editor.remove("call_info_json") }
        verify { editor.apply() }
    }

    @Test
    fun `getSavedRecordingState returns null when no recording state`() {
        // Given
        every { preferences.getBoolean("was_recording", false) } returns false

        // When
        val result = crashStateManager.getSavedRecordingState()

        // Then
        assert(result == null)
    }

    @Test
    fun `getSavedRecordingState returns recording state when available`() {
        // Given
        val startTime = System.currentTimeMillis()
        val filePath = "/path/to/recording.mp3"
        val callInfoJson = """
            {
                "callId": "test-call-123",
                "phoneNumber": "+1234567890",
                "contactName": "Test Contact",
                "isIncoming": true,
                "startTime": "2023-12-01T10:30:00"
            }
        """.trimIndent()

        every { preferences.getBoolean("was_recording", false) } returns true
        every { preferences.getLong("recording_start_time", 0L) } returns startTime
        every { preferences.getString("recording_file_path", null) } returns filePath
        every { preferences.getString("call_info_json", null) } returns callInfoJson

        // When
        val result = crashStateManager.getSavedRecordingState()

        // Then
        assert(result != null)
        assert(result!!.wasRecording)
        assert(result.recordingFilePath == filePath)
        assert(result.startTime == startTime)
        assert(result.callInfo?.callId == "test-call-123")
        assert(result.callInfo?.phoneNumber == "+1234567890")
        assert(result.callInfo?.contactName == "Test Contact")
        assert(result.callInfo?.isIncoming == true)
    }

    @Test
    fun `clearRecordingState removes all recording preferences`() {
        // When
        crashStateManager.clearRecordingState()

        // Then
        verify { editor.remove("was_recording") }
        verify { editor.remove("recording_start_time") }
        verify { editor.remove("call_info_json") }
        verify { editor.remove("recording_file_path") }
        verify { editor.apply() }
    }

    @Test
    fun `recordCrash increments crash count and updates timestamp`() {
        // Given
        every { preferences.getInt("crash_count", 0) } returns 2

        // When
        crashStateManager.recordCrash()

        // Then
        verify { editor.putLong("last_crash_time", any()) }
        verify { editor.putInt("crash_count", 3) }
        verify { editor.apply() }
    }

    @Test
    fun `getCrashStatistics returns correct statistics`() {
        // Given
        val lastCrashTime = System.currentTimeMillis()
        val crashCount = 5
        val appVersion = "1.2.3"

        every { preferences.getLong("last_crash_time", 0L) } returns lastCrashTime
        every { preferences.getInt("crash_count", 0) } returns crashCount
        every { preferences.getString("app_version", "unknown") } returns appVersion
        every { preferences.getBoolean("was_recording", false) } returns true

        // When
        val stats = crashStateManager.getCrashStatistics()

        // Then
        assert(stats.lastCrashTime == lastCrashTime)
        assert(stats.totalCrashes == crashCount)
        assert(stats.appVersion == appVersion)
        assert(stats.hasPendingRecovery)
    }

    @Test
    fun `saveActivityState saves activity information`() {
        // Given
        val activityName = "MainActivity"
        val extras = mapOf("key1" to "value1", "key2" to 123)

        // When
        crashStateManager.saveActivityState(activityName, extras)

        // Then
        verify { editor.putString("last_activity", any()) }
        verify { editor.apply() }
    }

    @Test
    fun `getLastActivityState returns activity state when available`() {
        // Given
        val activityJson = """
            {
                "activity": "MainActivity",
                "timestamp": 1234567890,
                "extras": {
                    "key1": "value1",
                    "key2": 123
                }
            }
        """.trimIndent()

        every { preferences.getString("last_activity", null) } returns activityJson

        // When
        val result = crashStateManager.getLastActivityState()

        // Then
        assert(result != null)
        assert(result!!.activityName == "MainActivity")
        assert(result.timestamp == 1234567890L)
        assert(result.extras["key1"] == "value1")
        assert(result.extras["key2"] == 123)
    }

    @Test
    fun `getLastActivityState returns null when no activity state`() {
        // Given
        every { preferences.getString("last_activity", null) } returns null

        // When
        val result = crashStateManager.getLastActivityState()

        // Then
        assert(result == null)
    }

    @Test
    fun `backupSettings saves settings as JSON`() {
        // Given
        val settings = mapOf(
            "recording_quality" to "high",
            "auto_record" to true,
            "max_duration" to 3600
        )

        // When
        crashStateManager.backupSettings(settings)

        // Then
        verify { editor.putString("settings_backup", any()) }
        verify { editor.apply() }
    }

    @Test
    fun `restoreSettings returns settings map when available`() {
        // Given
        val settingsJson = """
            {
                "recording_quality": "high",
                "auto_record": true,
                "max_duration": 3600
            }
        """.trimIndent()

        every { preferences.getString("settings_backup", null) } returns settingsJson

        // When
        val result = crashStateManager.restoreSettings()

        // Then
        assert(result != null)
        assert(result!!["recording_quality"] == "high")
        assert(result["auto_record"] == true)
        assert(result["max_duration"] == 3600)
    }

    @Test
    fun `restoreSettings returns null when no backup available`() {
        // Given
        every { preferences.getString("settings_backup", null) } returns null

        // When
        val result = crashStateManager.restoreSettings()

        // Then
        assert(result == null)
    }

    @Test
    fun `wasRecentCrash returns true for recent crash`() {
        // Given
        val recentTime = System.currentTimeMillis() - 10000L // 10 seconds ago
        every { preferences.getLong("last_crash_time", 0L) } returns recentTime

        // When
        val result = crashStateManager.wasRecentCrash(30000L) // 30 second threshold

        // Then
        assert(result)
    }

    @Test
    fun `wasRecentCrash returns false for old crash`() {
        // Given
        val oldTime = System.currentTimeMillis() - 60000L // 60 seconds ago
        every { preferences.getLong("last_crash_time", 0L) } returns oldTime

        // When
        val result = crashStateManager.wasRecentCrash(30000L) // 30 second threshold

        // Then
        assert(!result)
    }

    @Test
    fun `wasRecentCrash returns false when no crash recorded`() {
        // Given
        every { preferences.getLong("last_crash_time", 0L) } returns 0L

        // When
        val result = crashStateManager.wasRecentCrash()

        // Then
        assert(!result)
    }

    @Test
    fun `performRecovery returns successful result with states`() = runTest {
        // Given
        val startTime = System.currentTimeMillis()
        val filePath = "/path/to/recording.mp3"
        val callInfoJson = """
            {
                "callId": "test-call-123",
                "phoneNumber": "+1234567890",
                "contactName": "Test Contact",
                "isIncoming": true,
                "startTime": "2023-12-01T10:30:00"
            }
        """.trimIndent()
        val activityJson = """
            {
                "activity": "MainActivity",
                "timestamp": 1234567890,
                "extras": {}
            }
        """.trimIndent()

        every { preferences.getBoolean("was_recording", false) } returns true
        every { preferences.getLong("recording_start_time", 0L) } returns startTime
        every { preferences.getString("recording_file_path", null) } returns filePath
        every { preferences.getString("call_info_json", null) } returns callInfoJson
        every { preferences.getString("last_activity", null) } returns activityJson
        every { preferences.getLong("last_crash_time", 0L) } returns System.currentTimeMillis()
        every { preferences.getInt("crash_count", 0) } returns 1
        every { preferences.getString("app_version", "unknown") } returns "1.0.0"

        // Mock file operations
        val recordingFile = mockk<File>(relaxed = true)
        mockkConstructor(File::class)
        every { anyConstructed<File>() } returns recordingFile
        every { recordingFile.exists() } returns true
        every { recordingFile.length() } returns 1024L // Non-empty file

        // When
        val result = crashStateManager.performRecovery()

        // Then
        assert(result.success)
        assert(result.recordingState != null)
        assert(result.activityState != null)
        assert(result.crashStatistics.totalCrashes == 1)
        assert(result.message == "Recovery completed successfully")
    }

    @Test
    fun `performRecovery removes empty recording files`() = runTest {
        // Given
        val filePath = "/path/to/empty_recording.mp3"
        
        every { preferences.getBoolean("was_recording", false) } returns true
        every { preferences.getString("recording_file_path", null) } returns filePath
        every { preferences.getString("call_info_json", null) } returns null
        every { preferences.getString("last_activity", null) } returns null
        every { preferences.getLong("last_crash_time", 0L) } returns 0L
        every { preferences.getInt("crash_count", 0) } returns 0
        every { preferences.getString("app_version", "unknown") } returns "1.0.0"

        // Mock empty file
        val recordingFile = mockk<File>(relaxed = true)
        mockkConstructor(File::class)
        every { anyConstructed<File>() } returns recordingFile
        every { recordingFile.exists() } returns true
        every { recordingFile.length() } returns 0L // Empty file
        every { recordingFile.delete() } returns true

        // When
        val result = crashStateManager.performRecovery()

        // Then
        assert(result.success)
        verify { recordingFile.delete() }
    }

    @Test
    fun `resetCrashCount clears crash statistics`() {
        // When
        crashStateManager.resetCrashCount()

        // Then
        verify { editor.remove("crash_count") }
        verify { editor.remove("last_crash_time") }
        verify { editor.apply() }
    }
}