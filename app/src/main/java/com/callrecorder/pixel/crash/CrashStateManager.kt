package com.callrecorder.pixel.crash

import android.content.Context
import android.content.SharedPreferences
import com.callrecorder.pixel.data.model.CallInfo
import com.callrecorder.pixel.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * Manages application state during crashes to enable recovery and continuation
 * of operations after app restart.
 */
class CrashStateManager(private val context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val stateFile = File(context.filesDir, STATE_FILE_NAME)
    private val logger = Logger.getInstance(context)

    companion object {
        private const val TAG = "CrashStateManager"
        private const val PREFS_NAME = "crash_state_prefs"
        private const val STATE_FILE_NAME = "crash_state.json"
        
        // Preference keys
        private const val KEY_WAS_RECORDING = "was_recording"
        private const val KEY_RECORDING_START_TIME = "recording_start_time"
        private const val KEY_CALL_INFO_JSON = "call_info_json"
        private const val KEY_RECORDING_FILE_PATH = "recording_file_path"
        private const val KEY_LAST_CRASH_TIME = "last_crash_time"
        private const val KEY_CRASH_COUNT = "crash_count"
        private const val KEY_APP_VERSION = "app_version"
        private const val KEY_LAST_ACTIVITY = "last_activity"
        private const val KEY_SETTINGS_BACKUP = "settings_backup"
    }

    /**
     * Saves the current recording state
     */
    fun saveRecordingState(
        isRecording: Boolean,
        callInfo: CallInfo?,
        recordingFilePath: String?,
        startTime: Long?
    ) {
        logger.debug(TAG, "Saving recording state: recording=$isRecording")
        
        try {
            preferences.edit().apply {
                putBoolean(KEY_WAS_RECORDING, isRecording)
                putLong(KEY_RECORDING_START_TIME, startTime ?: 0L)
                putString(KEY_RECORDING_FILE_PATH, recordingFilePath)
                
                // Serialize call info to JSON
                callInfo?.let { info ->
                    val callInfoJson = serializeCallInfo(info)
                    putString(KEY_CALL_INFO_JSON, callInfoJson)
                } ?: remove(KEY_CALL_INFO_JSON)
                
                apply()
            }
            
            // Also save to file for redundancy
            saveStateToFile()
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to save recording state", e)
        }
    }

    /**
     * Gets the saved recording state
     */
    fun getSavedRecordingState(): RecordingState? {
        logger.debug(TAG, "Retrieving saved recording state")
        
        return try {
            val wasRecording = preferences.getBoolean(KEY_WAS_RECORDING, false)
            
            if (!wasRecording) {
                logger.debug(TAG, "No recording state found")
                return null
            }
            
            val startTime = preferences.getLong(KEY_RECORDING_START_TIME, 0L)
            val filePath = preferences.getString(KEY_RECORDING_FILE_PATH, null)
            val callInfoJson = preferences.getString(KEY_CALL_INFO_JSON, null)
            
            val callInfo = callInfoJson?.let { deserializeCallInfo(it) }
            
            RecordingState(
                wasRecording = wasRecording,
                callInfo = callInfo,
                recordingFilePath = filePath,
                startTime = if (startTime > 0) startTime else null
            )
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to retrieve recording state", e)
            null
        }
    }

    /**
     * Clears the saved recording state
     */
    fun clearRecordingState() {
        logger.debug(TAG, "Clearing recording state")
        
        try {
            preferences.edit().apply {
                remove(KEY_WAS_RECORDING)
                remove(KEY_RECORDING_START_TIME)
                remove(KEY_CALL_INFO_JSON)
                remove(KEY_RECORDING_FILE_PATH)
                apply()
            }
            
            // Also clear from file
            if (stateFile.exists()) {
                stateFile.delete()
            }
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to clear recording state", e)
        }
    }

    /**
     * Records a crash occurrence
     */
    fun recordCrash() {
        logger.info(TAG, "Recording crash occurrence")
        
        try {
            val currentTime = System.currentTimeMillis()
            val crashCount = preferences.getInt(KEY_CRASH_COUNT, 0) + 1
            
            preferences.edit().apply {
                putLong(KEY_LAST_CRASH_TIME, currentTime)
                putInt(KEY_CRASH_COUNT, crashCount)
                apply()
            }
            
            logger.warn(TAG, "Crash recorded. Total crashes: $crashCount")
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to record crash", e)
        }
    }

    /**
     * Gets crash statistics
     */
    fun getCrashStatistics(): CrashStatistics {
        return try {
            val lastCrashTime = preferences.getLong(KEY_LAST_CRASH_TIME, 0L)
            val crashCount = preferences.getInt(KEY_CRASH_COUNT, 0)
            val appVersion = preferences.getString(KEY_APP_VERSION, "unknown") ?: "unknown"
            
            CrashStatistics(
                lastCrashTime = if (lastCrashTime > 0) lastCrashTime else null,
                totalCrashes = crashCount,
                appVersion = appVersion,
                hasPendingRecovery = getSavedRecordingState() != null
            )
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to get crash statistics", e)
            CrashStatistics(null, 0, "unknown", false)
        }
    }

    /**
     * Saves the current activity state
     */
    fun saveActivityState(activityName: String, extras: Map<String, Any> = emptyMap()) {
        logger.debug(TAG, "Saving activity state: $activityName")
        
        try {
            val stateJson = JSONObject().apply {
                put("activity", activityName)
                put("timestamp", System.currentTimeMillis())
                put("extras", JSONObject(extras))
            }
            
            preferences.edit().apply {
                putString(KEY_LAST_ACTIVITY, stateJson.toString())
                apply()
            }
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to save activity state", e)
        }
    }

    /**
     * Gets the last saved activity state
     */
    fun getLastActivityState(): ActivityState? {
        return try {
            val stateJson = preferences.getString(KEY_LAST_ACTIVITY, null) ?: return null
            val jsonObject = JSONObject(stateJson)
            
            ActivityState(
                activityName = jsonObject.getString("activity"),
                timestamp = jsonObject.getLong("timestamp"),
                extras = jsonObject.optJSONObject("extras")?.let { extrasJson ->
                    val map = mutableMapOf<String, Any>()
                    extrasJson.keys().forEach { key ->
                        map[key] = extrasJson.get(key)
                    }
                    map
                } ?: emptyMap()
            )
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to get activity state", e)
            null
        }
    }

    /**
     * Backs up application settings
     */
    fun backupSettings(settings: Map<String, Any>) {
        logger.debug(TAG, "Backing up application settings")
        
        try {
            val settingsJson = JSONObject(settings)
            
            preferences.edit().apply {
                putString(KEY_SETTINGS_BACKUP, settingsJson.toString())
                apply()
            }
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to backup settings", e)
        }
    }

    /**
     * Restores application settings
     */
    fun restoreSettings(): Map<String, Any>? {
        return try {
            val settingsJson = preferences.getString(KEY_SETTINGS_BACKUP, null) ?: return null
            val jsonObject = JSONObject(settingsJson)
            
            val settings = mutableMapOf<String, Any>()
            jsonObject.keys().forEach { key ->
                settings[key] = jsonObject.get(key)
            }
            
            logger.info(TAG, "Restored ${settings.size} settings from backup")
            settings
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to restore settings", e)
            null
        }
    }

    /**
     * Checks if the app crashed recently
     */
    fun wasRecentCrash(thresholdMs: Long = 30000L): Boolean { // 30 seconds
        val lastCrashTime = preferences.getLong(KEY_LAST_CRASH_TIME, 0L)
        return if (lastCrashTime > 0) {
            System.currentTimeMillis() - lastCrashTime < thresholdMs
        } else {
            false
        }
    }

    /**
     * Performs recovery actions after a crash
     */
    suspend fun performRecovery(): RecoveryResult {
        logger.info(TAG, "Performing crash recovery")
        
        return try {
            val recordingState = getSavedRecordingState()
            val activityState = getLastActivityState()
            val crashStats = getCrashStatistics()
            
            // Clean up any corrupted recording files
            recordingState?.recordingFilePath?.let { filePath ->
                val file = File(filePath)
                if (file.exists() && file.length() == 0L) {
                    logger.warn(TAG, "Removing empty recording file: $filePath")
                    file.delete()
                }
            }
            
            // Reset crash-sensitive states
            clearRecordingState()
            
            RecoveryResult(
                success = true,
                recordingState = recordingState,
                activityState = activityState,
                crashStatistics = crashStats,
                message = "Recovery completed successfully"
            )
            
        } catch (e: Exception) {
            logger.error(TAG, "Recovery failed", e)
            RecoveryResult(
                success = false,
                recordingState = null,
                activityState = null,
                crashStatistics = getCrashStatistics(),
                message = "Recovery failed: ${e.message}"
            )
        }
    }

    /**
     * Saves state to file for redundancy
     */
    private fun saveStateToFile() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = JSONObject().apply {
                    put("wasRecording", preferences.getBoolean(KEY_WAS_RECORDING, false))
                    put("startTime", preferences.getLong(KEY_RECORDING_START_TIME, 0L))
                    put("filePath", preferences.getString(KEY_RECORDING_FILE_PATH, ""))
                    put("callInfo", preferences.getString(KEY_CALL_INFO_JSON, ""))
                    put("timestamp", System.currentTimeMillis())
                }
                
                stateFile.writeText(state.toString())
                
            } catch (e: Exception) {
                logger.error(TAG, "Failed to save state to file", e)
            }
        }
    }

    /**
     * Serializes CallInfo to JSON
     */
    private fun serializeCallInfo(callInfo: CallInfo): String {
        return try {
            JSONObject().apply {
                put("callId", callInfo.callId)
                put("phoneNumber", callInfo.phoneNumber)
                put("contactName", callInfo.contactName ?: "")
                put("isIncoming", callInfo.isIncoming)
                put("startTime", callInfo.startTime.toString())
            }.toString()
        } catch (e: JSONException) {
            logger.error(TAG, "Failed to serialize CallInfo", e)
            ""
        }
    }

    /**
     * Deserializes CallInfo from JSON
     */
    private fun deserializeCallInfo(json: String): CallInfo? {
        return try {
            val jsonObject = JSONObject(json)
            CallInfo(
                callId = jsonObject.getString("callId"),
                phoneNumber = jsonObject.getString("phoneNumber"),
                contactName = jsonObject.getString("contactName").takeIf { it.isNotEmpty() },
                isIncoming = jsonObject.getBoolean("isIncoming"),
                startTime = java.time.LocalDateTime.parse(jsonObject.getString("startTime"))
            )
        } catch (e: Exception) {
            logger.error(TAG, "Failed to deserialize CallInfo", e)
            null
        }
    }

    /**
     * Resets crash count (call after successful operation period)
     */
    fun resetCrashCount() {
        logger.info(TAG, "Resetting crash count")
        
        preferences.edit().apply {
            remove(KEY_CRASH_COUNT)
            remove(KEY_LAST_CRASH_TIME)
            apply()
        }
    }
}

/**
 * Data classes for crash state management
 */
data class RecordingState(
    val wasRecording: Boolean,
    val callInfo: CallInfo?,
    val recordingFilePath: String?,
    val startTime: Long?
)

data class ActivityState(
    val activityName: String,
    val timestamp: Long,
    val extras: Map<String, Any>
)

data class CrashStatistics(
    val lastCrashTime: Long?,
    val totalCrashes: Int,
    val appVersion: String,
    val hasPendingRecovery: Boolean
)

data class RecoveryResult(
    val success: Boolean,
    val recordingState: RecordingState?,
    val activityState: ActivityState?,
    val crashStatistics: CrashStatistics,
    val message: String
)