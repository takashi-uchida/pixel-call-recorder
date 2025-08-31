package com.callrecorder.pixel.data.repository

import android.content.Context
import com.callrecorder.pixel.data.database.CallRecorderDatabase
import com.callrecorder.pixel.data.database.DatabaseIntegrityResult
import com.callrecorder.pixel.data.dao.RecordingDao
import com.callrecorder.pixel.data.dao.AudioQualityStats
import com.callrecorder.pixel.data.dao.DirectionCount
import com.callrecorder.pixel.data.dao.PhoneNumberStats
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.CallInfo
import com.callrecorder.pixel.data.model.Recording
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.LocalDateTime

/**
 * Unit tests for RecordingRepository.
 * Tests the integration between FileManager and database operations.
 */
class RecordingRepositoryTest {

    @MockK
    private lateinit var mockContext: Context

    @MockK
    private lateinit var mockDatabase: CallRecorderDatabase

    @MockK
    private lateinit var mockRecordingDao: RecordingDao

    private lateinit var repository: RecordingRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        every { mockDatabase.recordingDao() } returns mockRecordingDao
        
        // Mock static methods
        mockkObject(CallRecorderDatabase.Companion)
        every { CallRecorderDatabase.getDatabase(any()) } returns mockDatabase
        
        repository = RecordingRepository(mockContext, mockDatabase)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkObject(CallRecorderDatabase.Companion)
    }

    @Test
    fun `getAllRecordingsFlow should return flow from DAO`() = runTest {
        // Given
        val expectedRecordings = listOf(
            createTestRecording("1", "John Doe", "+1234567890"),
            createTestRecording("2", "Jane Smith", "+0987654321")
        )
        val expectedFlow = flowOf(expectedRecordings)
        
        every { mockRecordingDao.getAllRecordings() } returns expectedFlow

        // When
        val result = repository.getAllRecordingsFlow()

        // Then
        assertEquals("Should return flow from DAO", expectedFlow, result)
        verify { mockRecordingDao.getAllRecordings() }
    }

    @Test
    fun `getAllRecordings should return recordings list`() = runTest {
        // Given
        val expectedRecordings = listOf(
            createTestRecording("1", "John Doe", "+1234567890"),
            createTestRecording("2", "Jane Smith", "+0987654321")
        )
        
        coEvery { mockRecordingDao.getAllRecordingsList() } returns expectedRecordings

        // When
        val result = repository.getAllRecordings()

        // Then
        assertEquals("Should return recordings from DAO", expectedRecordings, result)
        coVerify { mockRecordingDao.getAllRecordingsList() }
    }

    @Test
    fun `getAllRecordings should return empty list on error`() = runTest {
        // Given
        coEvery { mockRecordingDao.getAllRecordingsList() } throws Exception("Database error")

        // When
        val result = repository.getAllRecordings()

        // Then
        assertTrue("Should return empty list on error", result.isEmpty())
    }

    @Test
    fun `getRecordingById should return recording when found`() = runTest {
        // Given
        val recordingId = "test123"
        val expectedRecording = createTestRecording(recordingId, "John Doe", "+1234567890")
        
        coEvery { mockRecordingDao.getRecordingById(recordingId) } returns expectedRecording

        // When
        val result = repository.getRecordingById(recordingId)

        // Then
        assertEquals("Should return expected recording", expectedRecording, result)
        coVerify { mockRecordingDao.getRecordingById(recordingId) }
    }

    @Test
    fun `getRecordingById should return null when not found`() = runTest {
        // Given
        val recordingId = "nonexistent"
        
        coEvery { mockRecordingDao.getRecordingById(recordingId) } returns null

        // When
        val result = repository.getRecordingById(recordingId)

        // Then
        assertNull("Should return null when not found", result)
    }

    @Test
    fun `createRecording should create file successfully`() = runTest {
        // Given
        val callInfo = CallInfo(
            callId = "test123",
            phoneNumber = "+1234567890",
            contactName = "John Doe",
            isIncoming = true,
            startTime = LocalDateTime.now()
        )

        // Mock external files directory
        val mockExternalDir = File.createTempFile("test", "dir").apply {
            delete()
            mkdirs()
        }
        every { mockContext.getExternalFilesDir(any()) } returns mockExternalDir

        // When
        val result = repository.createRecording(callInfo)

        // Then
        assertNotNull("Should create file successfully", result)
        assertTrue("File should exist", result!!.exists())
        
        // Cleanup
        result.delete()
        mockExternalDir.deleteRecursively()
    }

    @Test
    fun `saveRecording should save to database successfully`() = runTest {
        // Given
        val testFile = File.createTempFile("test", ".m4a")
        testFile.writeText("test audio data")
        
        val callInfo = CallInfo(
            callId = "test123",
            phoneNumber = "+1234567890",
            contactName = "John Doe",
            isIncoming = true,
            startTime = LocalDateTime.now()
        )
        
        val duration = 30000L
        val audioQuality = AudioQuality.STANDARD

        coEvery { mockRecordingDao.insertRecording(any()) } just Runs

        // When
        val result = repository.saveRecording(testFile, callInfo, duration, audioQuality)

        // Then
        assertTrue("Should save successfully", result)
        coVerify { mockRecordingDao.insertRecording(any()) }
        
        // Cleanup
        testFile.delete()
    }

    @Test
    fun `saveRecording should handle database error gracefully`() = runTest {
        // Given
        val testFile = File.createTempFile("test", ".m4a")
        val callInfo = CallInfo(
            callId = "test123",
            phoneNumber = "+1234567890",
            contactName = "John Doe",
            isIncoming = true,
            startTime = LocalDateTime.now()
        )

        coEvery { mockRecordingDao.insertRecording(any()) } throws Exception("Database error")

        // When
        val result = repository.saveRecording(testFile, callInfo, 30000L, AudioQuality.STANDARD)

        // Then
        assertFalse("Should return false on database error", result)
        
        // Cleanup
        testFile.delete()
    }

    @Test
    fun `deleteRecording should delete successfully`() = runTest {
        // Given
        val recordingId = "test123"
        val testFile = File.createTempFile("test", ".m4a")
        val recording = createTestRecording(recordingId, "John Doe", "+1234567890")
            .copy(filePath = testFile.absolutePath)
        
        coEvery { mockRecordingDao.getRecordingById(recordingId) } returns recording
        coEvery { mockRecordingDao.deleteRecordingById(recordingId) } just Runs

        // When
        val result = repository.deleteRecording(recordingId)

        // Then
        assertTrue("Should delete successfully", result)
        assertFalse("File should be deleted", testFile.exists())
        coVerify { mockRecordingDao.deleteRecordingById(recordingId) }
    }

    @Test
    fun `searchRecordings should return filtered results`() = runTest {
        // Given
        val query = "John"
        val expectedRecordings = listOf(
            createTestRecording("1", "John Doe", "+1234567890"),
            createTestRecording("2", "Johnny Smith", "+0987654321")
        )
        
        coEvery { mockRecordingDao.searchRecordings(query) } returns expectedRecordings

        // When
        val result = repository.searchRecordings(query)

        // Then
        assertEquals("Should return filtered recordings", expectedRecordings, result)
        coVerify { mockRecordingDao.searchRecordings(query) }
    }

    @Test
    fun `getRecordingsByPhoneNumber should return recordings for number`() = runTest {
        // Given
        val phoneNumber = "+1234567890"
        val expectedRecordings = listOf(
            createTestRecording("1", "John Doe", phoneNumber),
            createTestRecording("2", "John Doe", phoneNumber)
        )
        
        coEvery { mockRecordingDao.getRecordingsByPhoneNumber(phoneNumber) } returns expectedRecordings

        // When
        val result = repository.getRecordingsByPhoneNumber(phoneNumber)

        // Then
        assertEquals("Should return recordings for phone number", expectedRecordings, result)
        coVerify { mockRecordingDao.getRecordingsByPhoneNumber(phoneNumber) }
    }

    @Test
    fun `getRecordingsByDirection should filter by direction`() = runTest {
        // Given
        val isIncoming = true
        val expectedRecordings = listOf(
            createTestRecording("1", "John Doe", "+1234567890").copy(isIncoming = true)
        )
        
        coEvery { mockRecordingDao.getRecordingsByDirection(isIncoming) } returns expectedRecordings

        // When
        val result = repository.getRecordingsByDirection(isIncoming)

        // Then
        assertEquals("Should return recordings by direction", expectedRecordings, result)
        coVerify { mockRecordingDao.getRecordingsByDirection(isIncoming) }
    }

    @Test
    fun `getRecordingsByAudioQuality should filter by quality`() = runTest {
        // Given
        val audioQuality = AudioQuality.HIGH_QUALITY
        val expectedRecordings = listOf(
            createTestRecording("1", "John Doe", "+1234567890").copy(audioQuality = audioQuality)
        )
        
        coEvery { mockRecordingDao.getRecordingsByAudioQuality(audioQuality) } returns expectedRecordings

        // When
        val result = repository.getRecordingsByAudioQuality(audioQuality)

        // Then
        assertEquals("Should return recordings by audio quality", expectedRecordings, result)
        coVerify { mockRecordingDao.getRecordingsByAudioQuality(audioQuality) }
    }

    @Test
    fun `getTotalStorageUsed should return total size`() = runTest {
        // Given
        val expectedSize = 1024000L
        
        coEvery { mockRecordingDao.getTotalFileSize() } returns expectedSize

        // When
        val result = repository.getTotalStorageUsed()

        // Then
        assertEquals("Should return total storage used", expectedSize, result)
        coVerify { mockRecordingDao.getTotalFileSize() }
    }

    @Test
    fun `getRecordingStatistics should return comprehensive stats`() = runTest {
        // Given
        val totalCount = 10
        val totalSize = 1024000L
        val totalDuration = 300000L
        val averageSize = 102400.0
        val averageDuration = 30000.0
        
        val directionCounts = listOf(
            DirectionCount(isIncoming = true, count = 6),
            DirectionCount(isIncoming = false, count = 4)
        )
        
        val qualityStats = listOf(
            AudioQualityStats(AudioQuality.HIGH_QUALITY, 3, 500000L, 90000L),
            AudioQualityStats(AudioQuality.STANDARD, 7, 524000L, 210000L)
        )
        
        val phoneStats = listOf(
            PhoneNumberStats("+1234567890", "John Doe", 5, "2024-01-01T10:00:00"),
            PhoneNumberStats("+0987654321", "Jane Smith", 3, "2024-01-01T09:00:00")
        )

        coEvery { mockRecordingDao.getRecordingCount() } returns totalCount
        coEvery { mockRecordingDao.getTotalFileSize() } returns totalSize
        coEvery { mockRecordingDao.getTotalDuration() } returns totalDuration
        coEvery { mockRecordingDao.getAverageFileSize() } returns averageSize
        coEvery { mockRecordingDao.getAverageDuration() } returns averageDuration
        coEvery { mockRecordingDao.getRecordingCountByDirection() } returns directionCounts
        coEvery { mockRecordingDao.getStatsByAudioQuality() } returns qualityStats
        coEvery { mockRecordingDao.getPhoneNumberStats() } returns phoneStats

        // When
        val result = repository.getRecordingStatistics()

        // Then
        assertEquals("Total recordings should match", totalCount, result.totalRecordings)
        assertEquals("Total file size should match", totalSize, result.totalFileSize)
        assertEquals("Total duration should match", totalDuration, result.totalDuration)
        assertEquals("Incoming count should match", 6, result.incomingCount)
        assertEquals("Outgoing count should match", 4, result.outgoingCount)
        assertEquals("Quality breakdown should match", qualityStats, result.qualityBreakdown)
        assertEquals("Top contacts should be limited to 10", 2, result.topContacts.size)
    }

    @Test
    fun `getRecordingStatistics should handle database errors gracefully`() = runTest {
        // Given
        coEvery { mockRecordingDao.getRecordingCount() } throws Exception("Database error")

        // When
        val result = repository.getRecordingStatistics()

        // Then
        assertEquals("Should return default statistics on error", RecordingStatistics(), result)
    }

    @Test
    fun `performDatabaseIntegrityCheck should delegate to database`() = runTest {
        // Given
        val expectedResult = DatabaseIntegrityResult.Success(5, 1, listOf("Issue 1"))
        
        every { CallRecorderDatabase.performIntegrityCheck(mockContext) } returns expectedResult

        // When
        val result = repository.performDatabaseIntegrityCheck()

        // Then
        assertEquals("Should return integrity check result", expectedResult, result)
        verify { CallRecorderDatabase.performIntegrityCheck(mockContext) }
    }

    @Test
    fun `validateAllRecordings should check all recordings`() = runTest {
        // Given
        val testFile1 = File.createTempFile("test1", ".m4a")
        testFile1.writeText("test audio data")
        
        val testFile2 = File.createTempFile("test2", ".m4a")
        // Don't create file2 to simulate missing file
        testFile2.delete()
        
        val recording1 = createTestRecording("1", "John Doe", "+1234567890")
            .copy(filePath = testFile1.absolutePath, fileSize = testFile1.length())
        val recording2 = createTestRecording("2", "Jane Smith", "+0987654321")
            .copy(filePath = testFile2.absolutePath)
        
        val allRecordings = listOf(recording1, recording2)
        
        coEvery { mockRecordingDao.getAllRecordingsList() } returns allRecordings

        // When
        val result = repository.validateAllRecordings()

        // Then
        assertEquals("Should have 1 valid recording", 1, result.validCount)
        assertEquals("Should have 1 invalid recording", 1, result.invalidCount)
        assertTrue("Should have issues listed", result.issues.isNotEmpty())
        
        // Cleanup
        testFile1.delete()
    }

    @Test
    fun `RecordingStatistics getFormattedTotalSize should format sizes correctly`() {
        // Test bytes
        val statsBytes = RecordingStatistics(totalFileSize = 512L)
        assertEquals("512B", statsBytes.getFormattedTotalSize())
        
        // Test KB
        val statsKB = RecordingStatistics(totalFileSize = 2048L)
        assertEquals("2KB", statsKB.getFormattedTotalSize())
        
        // Test MB
        val statsMB = RecordingStatistics(totalFileSize = 1536000L) // 1.5MB
        assertEquals("1.5MB", statsMB.getFormattedTotalSize())
        
        // Test GB
        val statsGB = RecordingStatistics(totalFileSize = 1610612736L) // 1.5GB
        assertEquals("1.5GB", statsGB.getFormattedTotalSize())
    }

    @Test
    fun `RecordingStatistics getFormattedTotalDuration should format duration correctly`() {
        // Test seconds only
        val statsSeconds = RecordingStatistics(totalDuration = 45000L) // 45 seconds
        assertEquals("00:45", statsSeconds.getFormattedTotalDuration())
        
        // Test minutes and seconds
        val statsMinutes = RecordingStatistics(totalDuration = 150000L) // 2:30
        assertEquals("02:30", statsMinutes.getFormattedTotalDuration())
        
        // Test hours, minutes, and seconds
        val statsHours = RecordingStatistics(totalDuration = 3723000L) // 1:02:03
        assertEquals("1:02:03", statsHours.getFormattedTotalDuration())
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