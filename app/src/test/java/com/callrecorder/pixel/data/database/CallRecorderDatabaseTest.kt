package com.callrecorder.pixel.data.database

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.callrecorder.pixel.data.dao.RecordingDao
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.Recording
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.LocalDateTime

/**
 * Unit tests for CallRecorderDatabase and RecordingDao.
 * Tests database operations, migrations, and data integrity.
 */
@RunWith(RobolectricTestRunner::class)
class CallRecorderDatabaseTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: CallRecorderDatabase
    private lateinit var recordingDao: RecordingDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CallRecorderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recordingDao = database.recordingDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insertRecording should save recording to database`() = runTest {
        // Given
        val recording = createTestRecording("test1", "John Doe", "+1234567890")

        // When
        recordingDao.insertRecording(recording)
        val result = recordingDao.getRecordingById("test1")

        // Then
        assertNotNull("Recording should be saved", result)
        assertEquals("ID should match", recording.id, result!!.id)
        assertEquals("Contact name should match", recording.contactName, result.contactName)
        assertEquals("Phone number should match", recording.phoneNumber, result.phoneNumber)
    }

    @Test
    fun `getAllRecordingsList should return recordings ordered by date`() = runTest {
        // Given
        val recording1 = createTestRecording("test1", "John Doe", "+1234567890")
            .copy(recordingDate = LocalDateTime.now().minusDays(2))
        val recording2 = createTestRecording("test2", "Jane Smith", "+0987654321")
            .copy(recordingDate = LocalDateTime.now().minusDays(1))
        val recording3 = createTestRecording("test3", "Bob Johnson", "+1122334455")
            .copy(recordingDate = LocalDateTime.now())

        recordingDao.insertRecording(recording1)
        recordingDao.insertRecording(recording2)
        recordingDao.insertRecording(recording3)

        // When
        val result = recordingDao.getAllRecordingsList()

        // Then
        assertEquals("Should return 3 recordings", 3, result.size)
        assertEquals("First should be most recent", "test3", result[0].id)
        assertEquals("Second should be middle", "test2", result[1].id)
        assertEquals("Third should be oldest", "test1", result[2].id)
    }

    @Test
    fun `getRecordingById should return correct recording`() = runTest {
        // Given
        val recording1 = createTestRecording("test1", "John Doe", "+1234567890")
        val recording2 = createTestRecording("test2", "Jane Smith", "+0987654321")

        recordingDao.insertRecording(recording1)
        recordingDao.insertRecording(recording2)

        // When
        val result = recordingDao.getRecordingById("test2")

        // Then
        assertNotNull("Recording should be found", result)
        assertEquals("Should return correct recording", "test2", result!!.id)
        assertEquals("Contact name should match", "Jane Smith", result.contactName)
    }

    @Test
    fun `getRecordingById should return null for non-existent recording`() = runTest {
        // When
        val result = recordingDao.getRecordingById("nonexistent")

        // Then
        assertNull("Should return null for non-existent recording", result)
    }

    @Test
    fun `getRecordingsByPhoneNumber should return recordings for specific number`() = runTest {
        // Given
        val phoneNumber = "+1234567890"
        val recording1 = createTestRecording("test1", "John Doe", phoneNumber)
        val recording2 = createTestRecording("test2", "John Doe", phoneNumber)
        val recording3 = createTestRecording("test3", "Jane Smith", "+0987654321")

        recordingDao.insertRecording(recording1)
        recordingDao.insertRecording(recording2)
        recordingDao.insertRecording(recording3)

        // When
        val result = recordingDao.getRecordingsByPhoneNumber(phoneNumber)

        // Then
        assertEquals("Should return 2 recordings", 2, result.size)
        assertTrue("Should contain recording1", result.any { it.id == "test1" })
        assertTrue("Should contain recording2", result.any { it.id == "test2" })
        assertFalse("Should not contain recording3", result.any { it.id == "test3" })
    }

    @Test
    fun `searchRecordings should find recordings by contact name`() = runTest {
        // Given
        val recording1 = createTestRecording("test1", "John Doe", "+1234567890")
        val recording2 = createTestRecording("test2", "Johnny Smith", "+0987654321")
        val recording3 = createTestRecording("test3", "Jane Smith", "+1122334455")

        recordingDao.insertRecording(recording1)
        recordingDao.insertRecording(recording2)
        recordingDao.insertRecording(recording3)

        // When
        val result = recordingDao.searchRecordings("John")

        // Then
        assertEquals("Should return 2 recordings", 2, result.size)
        assertTrue("Should contain John Doe", result.any { it.contactName == "John Doe" })
        assertTrue("Should contain Johnny Smith", result.any { it.contactName == "Johnny Smith" })
    }

    @Test
    fun `searchRecordings should find recordings by phone number`() = runTest {
        // Given
        val recording1 = createTestRecording("test1", "John Doe", "+1234567890")
        val recording2 = createTestRecording("test2", "Jane Smith", "+1234555555")
        val recording3 = createTestRecording("test3", "Bob Johnson", "+0987654321")

        recordingDao.insertRecording(recording1)
        recordingDao.insertRecording(recording2)
        recordingDao.insertRecording(recording3)

        // When
        val result = recordingDao.searchRecordings("1234")

        // Then
        assertEquals("Should return 2 recordings", 2, result.size)
        assertTrue("Should contain recordings with 1234 in phone number", 
            result.all { it.phoneNumber.contains("1234") })
    }

    @Test
    fun `getRecordingsByDirection should filter by incoming outgoing`() = runTest {
        // Given
        val incomingRecording = createTestRecording("test1", "John Doe", "+1234567890")
            .copy(isIncoming = true)
        val outgoingRecording = createTestRecording("test2", "Jane Smith", "+0987654321")
            .copy(isIncoming = false)

        recordingDao.insertRecording(incomingRecording)
        recordingDao.insertRecording(outgoingRecording)

        // When
        val incomingResults = recordingDao.getRecordingsByDirection(true)
        val outgoingResults = recordingDao.getRecordingsByDirection(false)

        // Then
        assertEquals("Should return 1 incoming recording", 1, incomingResults.size)
        assertEquals("Should return 1 outgoing recording", 1, outgoingResults.size)
        assertTrue("Incoming result should be incoming", incomingResults[0].isIncoming)
        assertFalse("Outgoing result should be outgoing", outgoingResults[0].isIncoming)
    }

    @Test
    fun `getRecordingsByAudioQuality should filter by quality`() = runTest {
        // Given
        val highQualityRecording = createTestRecording("test1", "John Doe", "+1234567890")
            .copy(audioQuality = AudioQuality.HIGH_QUALITY)
        val standardRecording = createTestRecording("test2", "Jane Smith", "+0987654321")
            .copy(audioQuality = AudioQuality.STANDARD)

        recordingDao.insertRecording(highQualityRecording)
        recordingDao.insertRecording(standardRecording)

        // When
        val highQualityResults = recordingDao.getRecordingsByAudioQuality(AudioQuality.HIGH_QUALITY)
        val standardResults = recordingDao.getRecordingsByAudioQuality(AudioQuality.STANDARD)

        // Then
        assertEquals("Should return 1 high quality recording", 1, highQualityResults.size)
        assertEquals("Should return 1 standard recording", 1, standardResults.size)
        assertEquals("High quality result should match", AudioQuality.HIGH_QUALITY, highQualityResults[0].audioQuality)
        assertEquals("Standard result should match", AudioQuality.STANDARD, standardResults[0].audioQuality)
    }

    @Test
    fun `getRecordingCount should return correct count`() = runTest {
        // Given
        val recording1 = createTestRecording("test1", "John Doe", "+1234567890")
        val recording2 = createTestRecording("test2", "Jane Smith", "+0987654321")

        // When - Initially empty
        var count = recordingDao.getRecordingCount()
        assertEquals("Should be 0 initially", 0, count)

        // When - After inserting recordings
        recordingDao.insertRecording(recording1)
        recordingDao.insertRecording(recording2)
        count = recordingDao.getRecordingCount()

        // Then
        assertEquals("Should return 2 after inserting", 2, count)
    }

    @Test
    fun `getTotalFileSize should return sum of file sizes`() = runTest {
        // Given
        val recording1 = createTestRecording("test1", "John Doe", "+1234567890")
            .copy(fileSize = 1000L)
        val recording2 = createTestRecording("test2", "Jane Smith", "+0987654321")
            .copy(fileSize = 2000L)

        recordingDao.insertRecording(recording1)
        recordingDao.insertRecording(recording2)

        // When
        val totalSize = recordingDao.getTotalFileSize()

        // Then
        assertEquals("Should return sum of file sizes", 3000L, totalSize)
    }

    @Test
    fun `getTotalDuration should return sum of durations`() = runTest {
        // Given
        val recording1 = createTestRecording("test1", "John Doe", "+1234567890")
            .copy(duration = 30000L)
        val recording2 = createTestRecording("test2", "Jane Smith", "+0987654321")
            .copy(duration = 45000L)

        recordingDao.insertRecording(recording1)
        recordingDao.insertRecording(recording2)

        // When
        val totalDuration = recordingDao.getTotalDuration()

        // Then
        assertEquals("Should return sum of durations", 75000L, totalDuration)
    }

    @Test
    fun `getRecordingCountByDirection should group by direction`() = runTest {
        // Given
        val incomingRecording1 = createTestRecording("test1", "John Doe", "+1234567890")
            .copy(isIncoming = true)
        val incomingRecording2 = createTestRecording("test2", "Jane Smith", "+0987654321")
            .copy(isIncoming = true)
        val outgoingRecording = createTestRecording("test3", "Bob Johnson", "+1122334455")
            .copy(isIncoming = false)

        recordingDao.insertRecording(incomingRecording1)
        recordingDao.insertRecording(incomingRecording2)
        recordingDao.insertRecording(outgoingRecording)

        // When
        val directionCounts = recordingDao.getRecordingCountByDirection()

        // Then
        assertEquals("Should return 2 groups", 2, directionCounts.size)
        
        val incomingCount = directionCounts.find { it.isIncoming }?.count ?: 0
        val outgoingCount = directionCounts.find { !it.isIncoming }?.count ?: 0
        
        assertEquals("Should have 2 incoming recordings", 2, incomingCount)
        assertEquals("Should have 1 outgoing recording", 1, outgoingCount)
    }

    @Test
    fun `updateRecording should modify existing recording`() = runTest {
        // Given
        val originalRecording = createTestRecording("test1", "John Doe", "+1234567890")
        recordingDao.insertRecording(originalRecording)

        val updatedRecording = originalRecording.copy(contactName = "John Smith")

        // When
        recordingDao.updateRecording(updatedRecording)
        val result = recordingDao.getRecordingById("test1")

        // Then
        assertNotNull("Recording should still exist", result)
        assertEquals("Contact name should be updated", "John Smith", result!!.contactName)
        assertEquals("Other fields should remain same", originalRecording.phoneNumber, result.phoneNumber)
    }

    @Test
    fun `deleteRecordingById should remove recording`() = runTest {
        // Given
        val recording = createTestRecording("test1", "John Doe", "+1234567890")
        recordingDao.insertRecording(recording)

        // Verify it exists
        assertNotNull("Recording should exist before deletion", recordingDao.getRecordingById("test1"))

        // When
        recordingDao.deleteRecordingById("test1")

        // Then
        assertNull("Recording should be deleted", recordingDao.getRecordingById("test1"))
    }

    @Test
    fun `deleteRecordingsOlderThan should remove old recordings`() = runTest {
        // Given
        val oldDate = LocalDateTime.now().minusDays(35).toString()
        val recentDate = LocalDateTime.now().minusDays(10).toString()
        val cutoffDate = LocalDateTime.now().minusDays(30).toString()

        val oldRecording = createTestRecording("old", "Old Contact", "+1111111111")
            .copy(recordingDate = LocalDateTime.parse(oldDate))
        val recentRecording = createTestRecording("recent", "Recent Contact", "+2222222222")
            .copy(recordingDate = LocalDateTime.parse(recentDate))

        recordingDao.insertRecording(oldRecording)
        recordingDao.insertRecording(recentRecording)

        // When
        val deletedCount = recordingDao.deleteRecordingsOlderThan(cutoffDate)

        // Then
        assertEquals("Should delete 1 old recording", 1, deletedCount)
        assertNull("Old recording should be deleted", recordingDao.getRecordingById("old"))
        assertNotNull("Recent recording should remain", recordingDao.getRecordingById("recent"))
    }

    @Test
    fun `recordingExistsByPath should check file path existence`() = runTest {
        // Given
        val filePath = "/test/path/recording.m4a"
        val recording = createTestRecording("test1", "John Doe", "+1234567890")
            .copy(filePath = filePath)

        // When - Before insertion
        var exists = recordingDao.recordingExistsByPath(filePath)
        assertFalse("Should not exist before insertion", exists)

        // When - After insertion
        recordingDao.insertRecording(recording)
        exists = recordingDao.recordingExistsByPath(filePath)

        // Then
        assertTrue("Should exist after insertion", exists)
    }

    @Test
    fun `getPotentiallyCorruptedRecordings should find small files`() = runTest {
        // Given
        val normalRecording = createTestRecording("normal", "Normal Contact", "+1111111111")
            .copy(fileSize = 100000L) // 100KB
        val corruptedRecording = createTestRecording("corrupted", "Corrupted Contact", "+2222222222")
            .copy(fileSize = 500L) // 500 bytes - potentially corrupted

        recordingDao.insertRecording(normalRecording)
        recordingDao.insertRecording(corruptedRecording)

        // When
        val corruptedRecordings = recordingDao.getPotentiallyCorruptedRecordings(1024L) // 1KB minimum

        // Then
        assertEquals("Should find 1 potentially corrupted recording", 1, corruptedRecordings.size)
        assertEquals("Should be the corrupted recording", "corrupted", corruptedRecordings[0].id)
    }

    private fun createTestRecording(id: String, contactName: String, phoneNumber: String): Recording {
        return Recording(
            id = id,
            fileName = "test_$id.m4a",
            filePath = "/test/path/test_$id.m4a",
            contactName = contactName,
            phoneNumber = phoneNumber,
            duration = 30000L,
            fileSize = 1024000L,
            recordingDate = LocalDateTime.now(),
            audioQuality = AudioQuality.STANDARD,
            isIncoming = true
        )
    }
}