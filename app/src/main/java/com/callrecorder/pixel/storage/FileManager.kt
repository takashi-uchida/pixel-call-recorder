package com.callrecorder.pixel.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.callrecorder.pixel.data.dao.RecordingDao
import com.callrecorder.pixel.data.model.CallInfo
import com.callrecorder.pixel.data.model.Recording
import com.callrecorder.pixel.data.model.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Interface for file management operations.
 * Handles creation, storage, and management of recording files.
 */
interface FileManager {

    /**
     * Creates a new recording file with appropriate naming
     * @param callInfo Information about the call being recorded
     * @return File object for the new recording, or null if creation failed
     */
    suspend fun createRecordingFile(callInfo: CallInfo): File?

    /**
     * Saves a recording with metadata to the database
     * @param file The recording file
     * @param metadata Additional recording metadata
     * @return true if saved successfully, false otherwise
     */
    suspend fun saveRecording(file: File, metadata: RecordingMetadata): Boolean

    /**
     * Gets all recordings from storage
     * @return List of all recordings, ordered by date (newest first)
     */
    suspend fun getAllRecordings(): List<Recording>

    /**
     * Gets a specific recording by ID
     * @param recordingId The unique recording identifier
     * @return Recording object or null if not found
     */
    suspend fun getRecordingById(recordingId: String): Recording?

    /**
     * Deletes a recording and its associated file
     * @param recordingId The unique recording identifier
     * @return true if deleted successfully, false otherwise
     */
    suspend fun deleteRecording(recordingId: String): Boolean

    /**
     * Gets the file path for a specific recording
     * @param recordingId The unique recording identifier
     * @return File path string or null if not found
     */
    suspend fun getRecordingPath(recordingId: String): String?

    /**
     * Gets the File object for a specific recording
     * @param recordingId The unique recording identifier
     * @return File object or null if not found
     */
    suspend fun getRecordingFile(recordingId: String): File?

    /**
     * Searches recordings by query (contact name or phone number)
     * @param query Search query string
     * @return List of matching recordings
     */
    suspend fun searchRecordings(query: String): List<Recording>

    /**
     * Gets recordings for a specific phone number
     * @param phoneNumber The phone number to search for
     * @return List of recordings for the phone number
     */
    suspend fun getRecordingsByPhoneNumber(phoneNumber: String): List<Recording>

    /**
     * Gets total storage space used by recordings
     * @return Total size in bytes
     */
    suspend fun getTotalStorageUsed(): Long

    /**
     * Gets available storage space
     * @return Available space in bytes
     */
    suspend fun getAvailableStorage(): Long

    /**
     * Checks if there's enough space for a new recording
     * @param estimatedSize Estimated size of the new recording in bytes
     * @return true if enough space available, false otherwise
     */
    suspend fun hasEnoughSpace(estimatedSize: Long): Boolean

    /**
     * Cleans up old recordings based on retention policy
     * @param maxAge Maximum age in days for recordings to keep
     * @return Number of recordings deleted
     */
    suspend fun cleanupOldRecordings(maxAge: Int): Int

    /**
     * Exports a recording to external storage
     * @param recordingId The recording to export
     * @param destinationPath The destination path
     * @return true if exported successfully, false otherwise
     */
    suspend fun exportRecording(recordingId: String, destinationPath: String): Boolean

    /**
     * Validates the integrity of a recording file
     * @param recordingId The recording to validate
     * @return FileValidationResult indicating the validation status
     */
    suspend fun validateRecordingFile(recordingId: String): FileValidationResult

    /**
     * Returns the directory where recordings are stored.
     */
    fun recordingsDir(): File
}

/**
 * Concrete implementation of FileManager interface.
 * Handles file operations and integrates with Room database.
 */
class FileManagerImpl(
    private val context: Context,
    private val recordingDao: RecordingDao
) : FileManager {

    companion object {
        private const val TAG = "FileManager"
        private const val RECORDINGS_DIR = "CallRecordings"
        private const val FILE_EXTENSION = ".m4a"
        private const val MIN_FREE_SPACE_MB = 100L * 1024 * 1024 // 100MB
        private const val DATE_FORMAT = "yyyyMMdd_HHmmss"
    }

    private val recordingsDirectory: File by lazy {
        File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), RECORDINGS_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    override fun recordingsDir(): File = recordingsDirectory

    override suspend fun createRecordingFile(callInfo: CallInfo): File? = withContext(Dispatchers.IO) {
        try {
            val fileName = generateFileName(callInfo)
            val file = File(recordingsDirectory, fileName)
            
            // Ensure parent directory exists
            file.parentFile?.mkdirs()
            
            // Create the file
            if (file.createNewFile()) {
                Log.d(TAG, "Created recording file: ${file.absolutePath}")
                file
            } else {
                Log.e(TAG, "Failed to create recording file: ${file.absolutePath}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error creating recording file", e)
            null
        }
    }

    override suspend fun saveRecording(file: File, metadata: RecordingMetadata): Boolean = withContext(Dispatchers.IO) {
        try {
            val recording = Recording(
                id = UUID.randomUUID().toString(),
                fileName = file.name,
                filePath = file.absolutePath,
                contactName = metadata.callInfo.contactName,
                phoneNumber = metadata.callInfo.phoneNumber,
                duration = metadata.duration,
                fileSize = metadata.fileSize,
                recordingDate = LocalDateTime.now(),
                audioQuality = metadata.audioQuality,
                isIncoming = metadata.callInfo.isIncoming
            )

            recordingDao.insertRecording(recording)
            Log.d(TAG, "Saved recording to database: ${recording.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving recording to database", e)
            false
        }
    }

    override suspend fun getAllRecordings(): List<Recording> = withContext(Dispatchers.IO) {
        try {
            recordingDao.getAllRecordingsList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all recordings", e)
            emptyList()
        }
    }

    override suspend fun getRecordingById(recordingId: String): Recording? = withContext(Dispatchers.IO) {
        try {
            recordingDao.getRecordingById(recordingId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recording by ID: $recordingId", e)
            null
        }
    }

    override suspend fun deleteRecording(recordingId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val recording = recordingDao.getRecordingById(recordingId)
            if (recording != null) {
                // Delete the file
                val file = File(recording.filePath)
                val fileDeleted = if (file.exists()) file.delete() else true
                
                // Delete from database
                recordingDao.deleteRecordingById(recordingId)
                
                Log.d(TAG, "Deleted recording: $recordingId, file deleted: $fileDeleted")
                true
            } else {
                Log.w(TAG, "Recording not found for deletion: $recordingId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting recording: $recordingId", e)
            false
        }
    }

    override suspend fun getRecordingPath(recordingId: String): String? = withContext(Dispatchers.IO) {
        try {
            recordingDao.getRecordingById(recordingId)?.filePath
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recording path: $recordingId", e)
            null
        }
    }

    override suspend fun getRecordingFile(recordingId: String): File? = withContext(Dispatchers.IO) {
        try {
            val path = getRecordingPath(recordingId)
            path?.let { File(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recording file: $recordingId", e)
            null
        }
    }

    override suspend fun searchRecordings(query: String): List<Recording> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) {
                getAllRecordings()
            } else {
                recordingDao.searchRecordings(query.trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching recordings with query: $query", e)
            emptyList()
        }
    }

    override suspend fun getRecordingsByPhoneNumber(phoneNumber: String): List<Recording> = withContext(Dispatchers.IO) {
        try {
            recordingDao.getRecordingsByPhoneNumber(phoneNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recordings by phone number: $phoneNumber", e)
            emptyList()
        }
    }

    override suspend fun getTotalStorageUsed(): Long = withContext(Dispatchers.IO) {
        try {
            recordingDao.getTotalFileSize() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total storage used", e)
            0L
        }
    }

    override suspend fun getAvailableStorage(): Long = withContext(Dispatchers.IO) {
        try {
            val stat = StatFs(recordingsDirectory.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available storage", e)
            0L
        }
    }

    override suspend fun hasEnoughSpace(estimatedSize: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val availableSpace = getAvailableStorage()
            val requiredSpace = estimatedSize + MIN_FREE_SPACE_MB
            availableSpace >= requiredSpace
        } catch (e: Exception) {
            Log.e(TAG, "Error checking available space", e)
            false
        }
    }

    override suspend fun cleanupOldRecordings(maxAge: Int): Int = withContext(Dispatchers.IO) {
        try {
            val cutoffDate = LocalDateTime.now().minusDays(maxAge.toLong())
            val oldRecordings = recordingDao.getAllRecordingsList()
                .filter { it.recordingDate.isBefore(cutoffDate) }
            
            var deletedCount = 0
            oldRecordings.forEach { recording ->
                if (deleteRecording(recording.id)) {
                    deletedCount++
                }
            }
            
            Log.d(TAG, "Cleaned up $deletedCount old recordings (older than $maxAge days)")
            deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old recordings", e)
            0
        }
    }

    override suspend fun exportRecording(recordingId: String, destinationPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = getRecordingFile(recordingId)
            if (sourceFile == null || !sourceFile.exists()) {
                Log.e(TAG, "Source file not found for export: $recordingId")
                return@withContext false
            }

            val destinationFile = File(destinationPath)
            destinationFile.parentFile?.mkdirs()

            sourceFile.copyTo(destinationFile, overwrite = true)
            Log.d(TAG, "Exported recording $recordingId to $destinationPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting recording: $recordingId to $destinationPath", e)
            false
        }
    }

    override suspend fun validateRecordingFile(recordingId: String): FileValidationResult = withContext(Dispatchers.IO) {
        try {
            val recording = recordingDao.getRecordingById(recordingId)
            if (recording == null) {
                return@withContext FileValidationResult.Invalid("Recording not found in database")
            }

            val file = File(recording.filePath)
            when {
                !file.exists() -> FileValidationResult.Missing(recording.filePath)
                !file.canRead() -> FileValidationResult.Invalid("File is not readable")
                file.length() == 0L -> FileValidationResult.Invalid("File is empty")
                file.length() != recording.fileSize -> FileValidationResult.Invalid("File size mismatch")
                else -> FileValidationResult.Valid
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating recording file: $recordingId", e)
            FileValidationResult.Invalid("Validation error: ${e.message}")
        }
    }

    /**
     * Generates a unique filename for a recording based on call information
     */
    private fun generateFileName(callInfo: CallInfo): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT))
        val contactName = callInfo.contactName?.replace(Regex("[^a-zA-Z0-9]"), "_") ?: "Unknown"
        val direction = if (callInfo.isIncoming) "IN" else "OUT"
        val phoneNumber = callInfo.phoneNumber.replace(Regex("[^0-9]"), "").takeLast(4)
        
        return "${timestamp}_${direction}_${contactName}_${phoneNumber}${FILE_EXTENSION}"
    }
}

/**
 * Data class containing metadata for a recording
 */
data class RecordingMetadata(
    val callInfo: CallInfo,
    val duration: Long,
    val fileSize: Long,
    val audioQuality: AudioQuality
)

/**
 * Sealed class representing file validation results
 */
sealed class FileValidationResult {
    object Valid : FileValidationResult()
    data class Invalid(val reason: String) : FileValidationResult()
    data class Missing(val expectedPath: String) : FileValidationResult()
}
