package com.callrecorder.pixel.data.repository

import android.content.Context
import android.util.Log
import com.callrecorder.pixel.data.dao.RecordingDao
import com.callrecorder.pixel.data.database.CallRecorderDatabase
import com.callrecorder.pixel.data.database.DatabaseIntegrityResult
import com.callrecorder.pixel.data.model.Recording
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.CallInfo
import com.callrecorder.pixel.storage.FileManager
import com.callrecorder.pixel.storage.FileManagerImpl
import com.callrecorder.pixel.storage.RecordingMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

/**
 * Repository class that provides a clean API for recording data operations.
 * Combines database operations with file management.
 */
class RecordingRepository(
    private val context: Context,
    private val database: CallRecorderDatabase = CallRecorderDatabase.getDatabase(context)
) {
    
    companion object {
        private const val TAG = "RecordingRepository"
        
        @Volatile
        private var INSTANCE: RecordingRepository? = null
        
        fun getInstance(context: Context): RecordingRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = RecordingRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    private val recordingDao: RecordingDao = database.recordingDao()
    private val fileManager: FileManager = FileManagerImpl(context, recordingDao)

    // Simple insert passthrough (used by some tests to verify save flow)
    fun insertRecording(recording: Recording) {
        try {
            kotlinx.coroutines.runBlocking { recordingDao.insertRecording(recording) }
        } catch (_: Exception) { }
    }

    // Flow-based operations for reactive UI
    fun getAllRecordingsFlow(): Flow<List<Recording>> = recordingDao.getAllRecordings()

    // Recording CRUD operations
    suspend fun getAllRecordings(): List<Recording> = withContext(Dispatchers.IO) {
        try {
            fileManager.getAllRecordings()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all recordings", e)
            emptyList()
        }
    }

    suspend fun getRecordingById(recordingId: String): Recording? = withContext(Dispatchers.IO) {
        try {
            fileManager.getRecordingById(recordingId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recording by ID: $recordingId", e)
            null
        }
    }

    suspend fun createRecording(callInfo: CallInfo): File? = withContext(Dispatchers.IO) {
        try {
            fileManager.createRecordingFile(callInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating recording file", e)
            null
        }
    }

    suspend fun saveRecording(
        file: File,
        callInfo: CallInfo,
        duration: Long,
        audioQuality: AudioQuality
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val metadata = RecordingMetadata(
                callInfo = callInfo,
                duration = duration,
                fileSize = file.length(),
                audioQuality = audioQuality
            )
            fileManager.saveRecording(file, metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving recording", e)
            false
        }
    }

    suspend fun deleteRecording(recordingId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            fileManager.deleteRecording(recordingId)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting recording: $recordingId", e)
            false
        }
    }

    // Search and filter operations
    suspend fun searchRecordings(query: String): List<Recording> = withContext(Dispatchers.IO) {
        try {
            fileManager.searchRecordings(query)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching recordings", e)
            emptyList()
        }
    }

    suspend fun getRecordingsByPhoneNumber(phoneNumber: String): List<Recording> = withContext(Dispatchers.IO) {
        try {
            fileManager.getRecordingsByPhoneNumber(phoneNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recordings by phone number", e)
            emptyList()
        }
    }

    suspend fun getRecordingsByDirection(isIncoming: Boolean): List<Recording> = withContext(Dispatchers.IO) {
        try {
            recordingDao.getRecordingsByDirection(isIncoming)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recordings by direction", e)
            emptyList()
        }
    }

    suspend fun getRecordingsByAudioQuality(audioQuality: AudioQuality): List<Recording> = withContext(Dispatchers.IO) {
        try {
            recordingDao.getRecordingsByAudioQuality(audioQuality)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recordings by audio quality", e)
            emptyList()
        }
    }

    // File operations
    suspend fun getRecordingFile(recordingId: String): File? = withContext(Dispatchers.IO) {
        try {
            fileManager.getRecordingFile(recordingId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recording file", e)
            null
        }
    }

    suspend fun exportRecording(recordingId: String, destinationPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            fileManager.exportRecording(recordingId, destinationPath)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting recording", e)
            false
        }
    }

    // Storage management
    suspend fun getTotalStorageUsed(): Long = withContext(Dispatchers.IO) {
        try {
            fileManager.getTotalStorageUsed()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total storage used", e)
            0L
        }
    }

    suspend fun getAvailableStorage(): Long = withContext(Dispatchers.IO) {
        try {
            fileManager.getAvailableStorage()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available storage", e)
            0L
        }
    }

    suspend fun hasEnoughSpace(estimatedSize: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            fileManager.hasEnoughSpace(estimatedSize)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking available space", e)
            false
        }
    }

    suspend fun cleanupOldRecordings(maxAge: Int): Int = withContext(Dispatchers.IO) {
        try {
            fileManager.cleanupOldRecordings(maxAge)
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old recordings", e)
            0
        }
    }

    // Statistics and analytics
    suspend fun getRecordingStatistics(): RecordingStatistics = withContext(Dispatchers.IO) {
        try {
            val totalCount = recordingDao.getRecordingCount()
            val totalSize = recordingDao.getTotalFileSize() ?: 0L
            val totalDuration = recordingDao.getTotalDuration() ?: 0L
            val averageSize = recordingDao.getAverageFileSize() ?: 0.0
            val averageDuration = recordingDao.getAverageDuration() ?: 0.0
            val directionCounts = recordingDao.getRecordingCountByDirection()
            val qualityStats = recordingDao.getStatsByAudioQuality()
            val phoneStats = recordingDao.getPhoneNumberStats()

            RecordingStatistics(
                totalRecordings = totalCount,
                totalFileSize = totalSize,
                totalDuration = totalDuration,
                averageFileSize = averageSize.toLong(),
                averageDuration = averageDuration.toLong(),
                incomingCount = directionCounts.find { it.isIncoming }?.count ?: 0,
                outgoingCount = directionCounts.find { !it.isIncoming }?.count ?: 0,
                qualityBreakdown = qualityStats,
                topContacts = phoneStats.take(10)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recording statistics", e)
            RecordingStatistics()
        }
    }

    // Maintenance operations
    suspend fun performDatabaseIntegrityCheck(): DatabaseIntegrityResult = withContext(Dispatchers.IO) {
        try {
            CallRecorderDatabase.performIntegrityCheck(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing integrity check", e)
            DatabaseIntegrityResult.Error("Integrity check failed: ${e.message}")
        }
    }

    suspend fun updateContactNames() = withContext(Dispatchers.IO) {
        try {
            // This would integrate with Android's ContactsContract to update contact names
            // For now, we'll just log that this functionality would be implemented
            Log.d(TAG, "Contact name update functionality would be implemented here")
            // Implementation would query contacts and update recordings with missing contact names
        } catch (e: Exception) {
            Log.e(TAG, "Error updating contact names", e)
        }
    }

    suspend fun validateAllRecordings(): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val allRecordings = recordingDao.getAllRecordingsList()
            var validCount = 0
            var invalidCount = 0
            val issues = mutableListOf<String>()

            allRecordings.forEach { recording ->
                try {
                    val file = File(recording.filePath)
                    when {
                        !file.exists() -> {
                            invalidCount++
                            issues.add("Recording ${recording.id}: File missing at ${recording.filePath}")
                        }
                        !file.canRead() -> {
                            invalidCount++
                            issues.add("Recording ${recording.id}: File is not readable")
                        }
                        file.length() == 0L -> {
                            invalidCount++
                            issues.add("Recording ${recording.id}: File is empty")
                        }
                        file.length() != recording.fileSize -> {
                            invalidCount++
                            issues.add("Recording ${recording.id}: File size mismatch")
                        }
                        else -> validCount++
                    }
                } catch (ve: Exception) {
                    invalidCount++
                    issues.add("Recording ${recording.id}: Validation error: ${ve.message}")
                }
            }

            ValidationResult(validCount, invalidCount, issues)
        } catch (e: Exception) {
            Log.e(TAG, "Error validating recordings", e)
            ValidationResult(0, 0, listOf("Validation failed: ${e.message}"))
        }
    }
}

/**
 * Data class containing recording statistics
 */
data class RecordingStatistics(
    val totalRecordings: Int = 0,
    val totalFileSize: Long = 0L,
    val totalDuration: Long = 0L,
    val averageFileSize: Long = 0L,
    val averageDuration: Long = 0L,
    val incomingCount: Int = 0,
    val outgoingCount: Int = 0,
    val qualityBreakdown: List<com.callrecorder.pixel.data.dao.AudioQualityStats> = emptyList(),
    val topContacts: List<com.callrecorder.pixel.data.dao.PhoneNumberStats> = emptyList()
) {
    fun getFormattedTotalSize(): String {
        return when {
            totalFileSize < 1024 -> "${totalFileSize}B"
            totalFileSize < 1024 * 1024 -> "${totalFileSize / 1024}KB"
            totalFileSize < 1024 * 1024 * 1024 -> String.format("%.1fMB", totalFileSize / (1024.0 * 1024.0))
            else -> String.format("%.1fGB", totalFileSize / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun getFormattedTotalDuration(): String {
        val totalSeconds = totalDuration / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%02d:%02d", minutes, seconds)
        }
    }
}

/**
 * Data class for validation results
 */
data class ValidationResult(
    val validCount: Int,
    val invalidCount: Int,
    val issues: List<String>
)
