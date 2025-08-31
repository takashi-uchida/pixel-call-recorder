package com.callrecorder.pixel.data.dao

import androidx.room.*
import com.callrecorder.pixel.data.model.Recording
import com.callrecorder.pixel.data.model.AudioQuality
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Recording entities.
 * Provides database operations for call recordings.
 */
@Dao
interface RecordingDao {

    /**
     * Gets all recordings ordered by recording date (newest first)
     */
    @Query("SELECT * FROM recordings ORDER BY recordingDate DESC")
    fun getAllRecordings(): Flow<List<Recording>>

    /**
     * Gets all recordings as a list (for synchronous operations)
     */
    @Query("SELECT * FROM recordings ORDER BY recordingDate DESC")
    suspend fun getAllRecordingsList(): List<Recording>

    /**
     * Gets a specific recording by ID
     */
    @Query("SELECT * FROM recordings WHERE id = :recordingId")
    suspend fun getRecordingById(recordingId: String): Recording?

    /**
     * Gets recordings for a specific phone number
     */
    @Query("SELECT * FROM recordings WHERE phoneNumber = :phoneNumber ORDER BY recordingDate DESC")
    suspend fun getRecordingsByPhoneNumber(phoneNumber: String): List<Recording>

    /**
     * Gets recordings within a date range
     */
    @Query("SELECT * FROM recordings WHERE recordingDate BETWEEN :startDate AND :endDate ORDER BY recordingDate DESC")
    suspend fun getRecordingsByDateRange(startDate: String, endDate: String): List<Recording>

    /**
     * Searches recordings by contact name or phone number
     */
    @Query("SELECT * FROM recordings WHERE contactName LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%' ORDER BY recordingDate DESC")
    suspend fun searchRecordings(query: String): List<Recording>

    /**
     * Gets recordings by call direction (incoming/outgoing)
     */
    @Query("SELECT * FROM recordings WHERE isIncoming = :isIncoming ORDER BY recordingDate DESC")
    suspend fun getRecordingsByDirection(isIncoming: Boolean): List<Recording>

    /**
     * Gets recordings by audio quality
     */
    @Query("SELECT * FROM recordings WHERE audioQuality = :audioQuality ORDER BY recordingDate DESC")
    suspend fun getRecordingsByAudioQuality(audioQuality: AudioQuality): List<Recording>

    /**
     * Gets recordings with duration greater than specified value
     */
    @Query("SELECT * FROM recordings WHERE duration > :minDuration ORDER BY recordingDate DESC")
    suspend fun getRecordingsLongerThan(minDuration: Long): List<Recording>

    /**
     * Gets recordings with file size greater than specified value
     */
    @Query("SELECT * FROM recordings WHERE fileSize > :minSize ORDER BY recordingDate DESC")
    suspend fun getRecordingsLargerThan(minSize: Long): List<Recording>

    /**
     * Gets the most recent recording for a phone number
     */
    @Query("SELECT * FROM recordings WHERE phoneNumber = :phoneNumber ORDER BY recordingDate DESC LIMIT 1")
    suspend fun getLatestRecordingForNumber(phoneNumber: String): Recording?

    /**
     * Gets recordings with missing contact names (null or empty)
     */
    @Query("SELECT * FROM recordings WHERE contactName IS NULL OR contactName = '' ORDER BY recordingDate DESC")
    suspend fun getRecordingsWithoutContactName(): List<Recording>

    /**
     * Gets total count of recordings
     */
    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun getRecordingCount(): Int

    /**
     * Gets count of recordings for a specific phone number
     */
    @Query("SELECT COUNT(*) FROM recordings WHERE phoneNumber = :phoneNumber")
    suspend fun getRecordingCountForNumber(phoneNumber: String): Int

    /**
     * Gets count of incoming vs outgoing recordings
     */
    @Query("SELECT isIncoming, COUNT(*) as count FROM recordings GROUP BY isIncoming")
    suspend fun getRecordingCountByDirection(): List<DirectionCount>

    /**
     * Gets total size of all recordings
     */
    @Query("SELECT SUM(fileSize) FROM recordings")
    suspend fun getTotalFileSize(): Long?

    /**
     * Gets total duration of all recordings
     */
    @Query("SELECT SUM(duration) FROM recordings")
    suspend fun getTotalDuration(): Long?

    /**
     * Gets average file size
     */
    @Query("SELECT AVG(fileSize) FROM recordings")
    suspend fun getAverageFileSize(): Double?

    /**
     * Gets average duration
     */
    @Query("SELECT AVG(duration) FROM recordings")
    suspend fun getAverageDuration(): Double?

    /**
     * Gets recordings statistics by audio quality
     */
    @Query("SELECT audioQuality, COUNT(*) as count, SUM(fileSize) as totalSize, SUM(duration) as totalDuration FROM recordings GROUP BY audioQuality")
    suspend fun getStatsByAudioQuality(): List<AudioQualityStats>

    /**
     * Gets unique phone numbers with recording counts
     */
    @Query("SELECT phoneNumber, contactName, COUNT(*) as recordingCount, MAX(recordingDate) as lastRecording FROM recordings GROUP BY phoneNumber ORDER BY recordingCount DESC")
    suspend fun getPhoneNumberStats(): List<PhoneNumberStats>

    /**
     * Inserts a new recording
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: Recording)

    /**
     * Inserts multiple recordings
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordings(recordings: List<Recording>)

    /**
     * Updates an existing recording
     */
    @Update
    suspend fun updateRecording(recording: Recording)

    /**
     * Updates contact name for all recordings with a specific phone number
     */
    @Query("UPDATE recordings SET contactName = :contactName WHERE phoneNumber = :phoneNumber AND (contactName IS NULL OR contactName = '')")
    suspend fun updateContactNameForNumber(phoneNumber: String, contactName: String)

    /**
     * Deletes a recording by ID
     */
    @Query("DELETE FROM recordings WHERE id = :recordingId")
    suspend fun deleteRecordingById(recordingId: String)

    /**
     * Deletes a recording entity
     */
    @Delete
    suspend fun deleteRecording(recording: Recording)

    /**
     * Deletes all recordings for a specific phone number
     */
    @Query("DELETE FROM recordings WHERE phoneNumber = :phoneNumber")
    suspend fun deleteRecordingsForNumber(phoneNumber: String)

    /**
     * Deletes all recordings
     */
    @Query("DELETE FROM recordings")
    suspend fun deleteAllRecordings()

    /**
     * Deletes recordings older than specified date
     */
    @Query("DELETE FROM recordings WHERE recordingDate < :cutoffDate")
    suspend fun deleteRecordingsOlderThan(cutoffDate: String): Int

    /**
     * Deletes recordings smaller than specified duration
     */
    @Query("DELETE FROM recordings WHERE duration < :minDuration")
    suspend fun deleteRecordingsShorterThan(minDuration: Long): Int

    /**
     * Checks if a recording exists by file path
     */
    @Query("SELECT EXISTS(SELECT 1 FROM recordings WHERE filePath = :filePath)")
    suspend fun recordingExistsByPath(filePath: String): Boolean

    /**
     * Gets recordings that may have file issues (file size is 0 or very small)
     */
    @Query("SELECT * FROM recordings WHERE fileSize < :minSize ORDER BY recordingDate DESC")
    suspend fun getPotentiallyCorruptedRecordings(minSize: Long = 1024): List<Recording>
}

/**
 * Data class for direction count results
 */
data class DirectionCount(
    val isIncoming: Boolean,
    val count: Int
)

/**
 * Data class for audio quality statistics
 */
data class AudioQualityStats(
    val audioQuality: AudioQuality,
    val count: Int,
    val totalSize: Long,
    val totalDuration: Long
)

/**
 * Data class for phone number statistics
 */
data class PhoneNumberStats(
    val phoneNumber: String,
    val contactName: String?,
    val recordingCount: Int,
    val lastRecording: String
)