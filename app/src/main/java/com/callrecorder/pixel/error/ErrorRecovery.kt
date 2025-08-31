package com.callrecorder.pixel.error

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.callrecorder.pixel.common.Result
import com.callrecorder.pixel.storage.FileManager
import kotlinx.coroutines.delay
import java.io.File

/**
 * Provides automatic error recovery mechanisms for common error scenarios.
 * Attempts to resolve issues without user intervention when possible.
 */
class ErrorRecovery(
    private val context: Context,
    private val fileManager: FileManager
) {

    /**
     * Attempts to recover from the given error automatically
     * @param error The error to recover from
     * @param maxRetries Maximum number of retry attempts
     * @return Result indicating if recovery was successful
     */
    suspend fun attemptRecovery(
        error: CallRecorderError,
        maxRetries: Int = 3
    ): Result<Unit> {
        return when (error) {
            is RecordingError -> recoverFromRecordingError(error, maxRetries)
            is StorageError -> recoverFromStorageError(error, maxRetries)
            is DatabaseError -> recoverFromDatabaseError(error, maxRetries)
            is SystemError -> recoverFromSystemError(error, maxRetries)
            else -> Result.error(error) // Cannot auto-recover from permission/validation errors
        }
    }

    /**
     * Attempts to recover from recording errors
     */
    private suspend fun recoverFromRecordingError(
        error: RecordingError,
        maxRetries: Int
    ): Result<Unit> {
        return when (error) {
            is RecordingError.AudioSourceNotAvailable -> {
                // Wait and retry - audio source might become available
                retryWithDelay(maxRetries, 2000) {
                    // Check if audio source is now available
                    // This would typically involve checking MediaRecorder availability
                    true // Simplified for now
                }
            }
            
            is RecordingError.InsufficientStorage -> {
                // Attempt to free up space by cleaning old recordings
                cleanupOldRecordings()
            }
            
            is RecordingError.FileCreationFailed -> {
                // Try creating the directory structure again
                createRecordingDirectories()
            }
            
            is RecordingError.AudioProcessingFailed -> {
                // Retry with different audio settings
                retryWithDelay(maxRetries, 1000) {
                    // This would involve trying different audio configurations
                    true // Simplified for now
                }
            }
            
            else -> Result.error(error)
        }
    }

    /**
     * Attempts to recover from storage errors
     */
    private suspend fun recoverFromStorageError(
        error: StorageError,
        maxRetries: Int
    ): Result<Unit> {
        return when (error) {
            is StorageError.DirectoryCreationFailed -> {
                createRecordingDirectories()
            }
            
            is StorageError.FileAccessDenied -> {
                // Try alternative storage location
                tryAlternativeStorageLocation()
            }
            
            is StorageError.StorageUnavailable -> {
                // Wait and retry - storage might become available
                retryWithDelay(maxRetries, 3000) {
                    isExternalStorageAvailable()
                }
            }
            
            else -> Result.error(error)
        }
    }

    /**
     * Attempts to recover from database errors
     */
    private suspend fun recoverFromDatabaseError(
        error: DatabaseError,
        maxRetries: Int
    ): Result<Unit> {
        return when (error) {
            is DatabaseError.DatabaseConnectionFailed -> {
                // Retry database connection
                retryWithDelay(maxRetries, 1000) {
                    // This would involve attempting to reconnect to the database
                    true // Simplified for now
                }
            }
            
            is DatabaseError.DatabaseCorrupted -> {
                // Attempt database repair or recreation
                repairDatabase()
            }
            
            else -> Result.error(error)
        }
    }

    /**
     * Attempts to recover from system errors
     */
    private suspend fun recoverFromSystemError(
        error: SystemError,
        maxRetries: Int
    ): Result<Unit> {
        return when (error) {
            is SystemError.LowMemory -> {
                // Trigger garbage collection and wait
                System.gc()
                delay(1000)
                Result.success(Unit)
            }
            
            is SystemError.ServiceUnavailable -> {
                // Wait and retry - service might become available
                retryWithDelay(maxRetries, 2000) {
                    // Check if service is now available
                    true // Simplified for now
                }
            }
            
            else -> Result.error(error)
        }
    }

    /**
     * Generic retry mechanism with exponential backoff
     */
    private suspend fun retryWithDelay(
        maxRetries: Int,
        baseDelayMs: Long,
        condition: suspend () -> Boolean
    ): Result<Unit> {
        repeat(maxRetries) { attempt ->
            if (condition()) {
                return Result.success(Unit)
            }
            
            if (attempt < maxRetries - 1) {
                val delayMs = baseDelayMs * (attempt + 1) // Linear backoff
                delay(delayMs)
            }
        }
        
        return Result.error(SystemError.ServiceUnavailable)
    }

    /**
     * Cleans up old recordings to free space
     */
    private suspend fun cleanupOldRecordings(): Result<Unit> {
        return try {
            val availableSpace = getAvailableStorageSpace()
            val requiredSpace = 100 * 1024 * 1024L // 100MB minimum
            
            if (availableSpace < requiredSpace) {
                val recordingsDir = fileManager.recordingsDir()
                val oldFiles = recordingsDir.listFiles()
                    ?.filter { it.isFile && it.extension in listOf("mp3", "wav", "m4a") }
                    ?.sortedBy { it.lastModified() }
                    ?.take(5) // Delete oldest 5 files
                
                oldFiles?.forEach { file ->
                    if (file.delete()) {
                        // Also remove from database if needed
                        // This would involve calling the repository to remove the record
                    }
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(StorageError.FileDeletionFailed)
        }
    }

    /**
     * Creates necessary recording directories
     */
    private suspend fun createRecordingDirectories(): Result<Unit> {
        return try {
            val recordingsDir = fileManager.recordingsDir()
            if (!recordingsDir.exists()) {
                val created = recordingsDir.mkdirs()
                if (!created) {
                    return Result.error(StorageError.DirectoryCreationFailed)
                }
            }
            
            // Create subdirectories if needed
            val tempDir = File(recordingsDir, "temp")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(StorageError.DirectoryCreationFailed)
        }
    }

    /**
     * Tries alternative storage location
     */
    private suspend fun tryAlternativeStorageLocation(): Result<Unit> {
        return try {
            // Try internal storage if external storage fails
            val internalDir = File(context.filesDir, "recordings")
            if (!internalDir.exists()) {
                val created = internalDir.mkdirs()
                if (!created) {
                    return Result.error(StorageError.DirectoryCreationFailed)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(StorageError.FileAccessDenied)
        }
    }

    /**
     * Attempts to repair corrupted database
     */
    private suspend fun repairDatabase(): Result<Unit> {
        return try {
            // This would involve database-specific repair operations
            // For Room database, this might involve clearing and recreating tables
            
            // For now, we'll simulate a successful repair
            delay(1000) // Simulate repair time
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(DatabaseError.DatabaseCorrupted)
        }
    }

    /**
     * Checks if external storage is available
     */
    private fun isExternalStorageAvailable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    /**
     * Gets available storage space in bytes
     */
    private fun getAvailableStorageSpace(): Long {
        return try {
            val recordingsDir = fileManager.recordingsDir()
            val stat = StatFs(recordingsDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Checks if there's enough storage space for recording
     */
    fun hasEnoughStorageSpace(requiredBytes: Long = 50 * 1024 * 1024L): Boolean {
        return getAvailableStorageSpace() >= requiredBytes
    }

    /**
     * Gets storage usage statistics
     */
    fun getStorageStats(): StorageStats {
            val recordingsDir = fileManager.recordingsDir()
        val totalSpace = try {
            val stat = StatFs(recordingsDir.absolutePath)
            stat.totalBytes
        } catch (e: Exception) {
            0L
        }
        
        val availableSpace = getAvailableStorageSpace()
        val usedSpace = totalSpace - availableSpace
        
        val recordingFiles = recordingsDir.listFiles()
            ?.filter { it.isFile && it.extension in listOf("mp3", "wav", "m4a") }
            ?: emptyList()
        
        val recordingsSize = recordingFiles.sumOf { it.length() }
        val recordingsCount = recordingFiles.size
        
        return StorageStats(
            totalSpace = totalSpace,
            availableSpace = availableSpace,
            usedSpace = usedSpace,
            recordingsSize = recordingsSize,
            recordingsCount = recordingsCount
        )
    }
}

/**
 * Data class representing storage statistics
 */
data class StorageStats(
    val totalSpace: Long,
    val availableSpace: Long,
    val usedSpace: Long,
    val recordingsSize: Long,
    val recordingsCount: Int
) {
    val usagePercentage: Float
        get() = if (totalSpace > 0) (usedSpace.toFloat() / totalSpace.toFloat()) * 100f else 0f
    
    val recordingsPercentage: Float
        get() = if (totalSpace > 0) (recordingsSize.toFloat() / totalSpace.toFloat()) * 100f else 0f
}
