package com.callrecorder.pixel.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.callrecorder.pixel.data.dao.RecordingDao
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.data.model.CallInfo
import com.callrecorder.pixel.data.model.Recording
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.LocalDateTime

/**
 * Unit tests for FileManagerImpl class.
 * Tests file creation, deletion, search functionality, and storage management.
 */
class FileManagerTest {

    @MockK
    private lateinit var mockContext: Context

    @MockK
    private lateinit var mockRecordingDao: RecordingDao

    @MockK
    private lateinit var mockFile: File

    @MockK
    private lateinit var mockStatFs: StatFs

    private lateinit var fileManager: FileManagerImpl
    private lateinit var testRecordingsDir: File

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        // Mock context and external files directory
        testRecordingsDir = File.createTempFile("test", "dir").apply {
            delete()
            mkdirs()
        }
        
        every { mockContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) } returns testRecordingsDir.parentFile
        
        fileManager = FileManagerImpl(mockContext, mockRecordingDao)
    }

    @After
    fun tearDown() {
        // Clean up test directory
        testRecordingsDir.deleteRecursively()
        clearAllMocks()
    }

    @Test
    fun `createRecordingFile should create file with correct naming pattern`() = runTest {
        // Given
        val callInfo = CallInfo(
            callId = "test123",
            phoneNumber = "+1234567890",
            contactName = "John Doe",
            isIncoming = true,
            startTime = LocalDateTime.now()
        )

        // When
        val result = fileManager.createRecordingFile(callInfo)

        // Then
        assertNotNull("File should be created", result)
        assertTrue("File should exist", result!!.exists())
        assertTrue("File name should contain direction", result.name.contains("IN"))
        assertTrue("File name should contain contact name", result.name.contains("John_Doe"))
        assertTrue("File name should have .m4a extension", result.name.endsWith(".m4a"))
    }

    @Test
    fun `createRecordingFile should handle null contact name`() = runTest {
        // Given
        val callInfo = CallInfo(
            callId = "test123",
            phoneNumber = "+1234567890",
            contactName = null,
            isIncoming = false,
            startTime = LocalDateTime.now()
        )

        // When
        val result = fileManager.createRecordingFile(callInfo)

        // Then
        assertNotNull("File should be created", result)
        assertTrue("File name should contain Unknown for null contact", result!!.name.contains("Unknown"))
        assertTrue("File name should contain OUT for outgoing", result.name.contains("OUT"))
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
        
        val metadata = RecordingMetadata(
            callInfo = callInfo,
            duration = 30000L,
            fileSize = testFile.length(),
            audioQuality = AudioQuality.STANDARD
        )

        coEvery { mockRecordingDao.insertRecording(any()) } just Runs

        // When
        val result = fileManager.saveRecording(testFile, metadata)

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
        
        val metadata = RecordingMetadata(
            callInfo = callInfo,
            duration = 30000L,
            fileSize = testFile.length(),
            audioQuality = AudioQuality.STANDARD
        )

        coEvery { mockRecordingDao.insertRecording(any()) } throws Exception("Database error")

        // When
        val result = fileManager.saveRecording(testFile, metadata)

        // Then
        assertFalse("Should return false on database error", result)
        
        // Cleanup
        testFile.delete()
    }

    @Test
    fun `getAllRecordings should return recordings from database`() = runTest {
        // Given
        val expectedRecordings = listOf(
            createTestRecording("1", "John Doe", "+1234567890"),
            createTestRecording("2", "Jane Smith", "+0987654321")
        )
        
        coEvery { mockRecordingDao.getAllRecordingsList() } returns expectedRecordings

        // When
        val result = fileManager.getAllRecordings()

        // Then
        assertEquals("Should return expected recordings", expectedRecordings, result)
        coVerify { mockRecordingDao.getAllRecordingsList() }
    }

    @Test
    fun `getAllRecordings should return empty list on database error`() = runTest {
        // Given
        coEvery { mockRecordingDao.getAllRecordingsList() } throws Exception("Database error")

        // When
        val result = fileManager.getAllRecordings()

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
        val result = fileManager.getRecordingById(recordingId)

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
        val result = fileManager.getRecordingById(recordingId)

        // Then
        assertNull("Should return null when not found", result)
    }

    @Test
    fun `deleteRecording should delete file and database record`() = runTest {
        // Given
        val recordingId = "test123"
        val testFile = File.createTempFile("test", ".m4a")
        val recording = createTestRecording(recordingId, "John Doe", "+1234567890")
            .copy(filePath = testFile.absolutePath)
        
        coEvery { mockRecordingDao.getRecordingById(recordingId) } returns recording
        coEvery { mockRecordingDao.deleteRecordingById(recordingId) } just Runs

        // When
        val result = fileManager.deleteRecording(recordingId)

        // Then
        assertTrue("Should delete successfully", result)
        assertFalse("File should be deleted", testFile.exists())
        coVerify { mockRecordingDao.deleteRecordingById(recordingId) }
    }

    @Test
    fun `deleteRecording should return false when recording not found`() = runTest {
        // Given
        val recordingId = "nonexistent"
        
        coEvery { mockRecordingDao.getRecordingById(recordingId) } returns null

        // When
        val result = fileManager.deleteRecording(recordingId)

        // Then
        assertFalse("Should return false when recording not found", result)
        coVerify(exactly = 0) { mockRecordingDao.deleteRecordingById(any()) }
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
        val result = fileManager.searchRecordings(query)

        // Then
        assertEquals("Should return filtered recordings", expectedRecordings, result)
        coVerify { mockRecordingDao.searchRecordings(query) }
    }

    @Test
    fun `searchRecordings should return all recordings for empty query`() = runTest {
        // Given
        val query = ""
        val allRecordings = listOf(
            createTestRecording("1", "John Doe", "+1234567890"),
            createTestRecording("2", "Jane Smith", "+0987654321")
        )
        
        coEvery { mockRecordingDao.getAllRecordingsList() } returns allRecordings

        // When
        val result = fileManager.searchRecordings(query)

        // Then
        assertEquals("Should return all recordings for empty query", allRecordings, result)
        coVerify { mockRecordingDao.getAllRecordingsList() }
    }

    @Test
    fun `getRecordingsByPhoneNumber should return recordings for specific number`() = runTest {
        // Given
        val phoneNumber = "+1234567890"
        val expectedRecordings = listOf(
            createTestRecording("1", "John Doe", phoneNumber),
            createTestRecording("2", "John Doe", phoneNumber)
        )
        
        coEvery { mockRecordingDao.getRecordingsByPhoneNumber(phoneNumber) } returns expectedRecordings

        // When
        val result = fileManager.getRecordingsByPhoneNumber(phoneNumber)

        // Then
        assertEquals("Should return recordings for phone number", expectedRecordings, result)
        coVerify { mockRecordingDao.getRecordingsByPhoneNumber(phoneNumber) }
    }

    @Test
    fun `getTotalStorageUsed should return total file size`() = runTest {
        // Given
        val expectedSize = 1024000L
        
        coEvery { mockRecordingDao.getTotalFileSize() } returns expectedSize

        // When
        val result = fileManager.getTotalStorageUsed()

        // Then
        assertEquals("Should return total storage used", expectedSize, result)
        coVerify { mockRecordingDao.getTotalFileSize() }
    }

    @Test
    fun `getTotalStorageUsed should return 0 when database returns null`() = runTest {
        // Given
        coEvery { mockRecordingDao.getTotalFileSize() } returns null

        // When
        val result = fileManager.getTotalStorageUsed()

        // Then
        assertEquals("Should return 0 when database returns null", 0L, result)
    }

    @Test
    fun `hasEnoughSpace should return true when sufficient space available`() = runTest {
        // Given
        val estimatedSize = 1000000L // 1MB
        
        // Mock StatFs to return sufficient space
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns 1000L
        every { anyConstructed<StatFs>().blockSizeLong } returns 1024L * 1024L // 1MB blocks
        
        // When
        val result = fileManager.hasEnoughSpace(estimatedSize)

        // Then
        assertTrue("Should return true when enough space available", result)
    }

    @Test
    fun `hasEnoughSpace should return false when insufficient space`() = runTest {
        // Given
        val estimatedSize = 1000000000L // 1GB
        
        // Mock StatFs to return insufficient space
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns 10L
        every { anyConstructed<StatFs>().blockSizeLong } returns 1024L // 1KB blocks
        
        // When
        val result = fileManager.hasEnoughSpace(estimatedSize)

        // Then
        assertFalse("Should return false when insufficient space", result)
    }

    @Test
    fun `cleanupOldRecordings should delete old recordings`() = runTest {
        // Given
        val maxAge = 30
        val oldDate = LocalDateTime.now().minusDays(35)
        val recentDate = LocalDateTime.now().minusDays(10)
        
        val oldRecording = createTestRecording("old", "Old Contact", "+1111111111")
            .copy(recordingDate = oldDate)
        val recentRecording = createTestRecording("recent", "Recent Contact", "+2222222222")
            .copy(recordingDate = recentDate)
        
        val allRecordings = listOf(oldRecording, recentRecording)
        
        coEvery { mockRecordingDao.getAllRecordingsList() } returns allRecordings
        coEvery { mockRecordingDao.getRecordingById("old") } returns oldRecording
        coEvery { mockRecordingDao.deleteRecordingById("old") } just Runs

        // When
        val result = fileManager.cleanupOldRecordings(maxAge)

        // Then
        assertEquals("Should delete 1 old recording", 1, result)
        coVerify { mockRecordingDao.deleteRecordingById("old") }
        coVerify(exactly = 0) { mockRecordingDao.deleteRecordingById("recent") }
    }

    @Test
    fun `exportRecording should copy file to destination`() = runTest {
        // Given
        val recordingId = "test123"
        val sourceFile = File.createTempFile("source", ".m4a")
        sourceFile.writeText("test audio data")
        
        val destinationPath = File.createTempFile("dest", ".m4a").absolutePath
        File(destinationPath).delete() // Remove the temp file so we can test creation
        
        val recording = createTestRecording(recordingId, "John Doe", "+1234567890")
            .copy(filePath = sourceFile.absolutePath)
        
        coEvery { mockRecordingDao.getRecordingById(recordingId) } returns recording

        // When
        val result = fileManager.exportRecording(recordingId, destinationPath)

        // Then
        assertTrue("Should export successfully", result)
        assertTrue("Destination file should exist", File(destinationPath).exists())
        assertEquals("File content should match", sourceFile.readText(), File(destinationPath).readText())
        
        // Cleanup
        sourceFile.delete()
        File(destinationPath).delete()
    }

    @Test
    fun `validateRecordingFile should return Valid for existing file`() = runTest {
        // Given
        val recordingId = "test123"
        val testFile = File.createTempFile("test", ".m4a")
        testFile.writeText("test audio data")
        
        val recording = createTestRecording(recordingId, "John Doe", "+1234567890")
            .copy(filePath = testFile.absolutePath, fileSize = testFile.length())
        
        coEvery { mockRecordingDao.getRecordingById(recordingId) } returns recording

        // When
        val result = fileManager.validateRecordingFile(recordingId)

        // Then
        assertTrue("Should return Valid", result is FileValidationResult.Valid)
        
        // Cleanup
        testFile.delete()
    }

    @Test
    fun `validateRecordingFile should return Missing for non-existent file`() = runTest {
        // Given
        val recordingId = "test123"
        val nonExistentPath = "/path/to/nonexistent/file.m4a"
        
        val recording = createTestRecording(recordingId, "John Doe", "+1234567890")
            .copy(filePath = nonExistentPath)
        
        coEvery { mockRecordingDao.getRecordingById(recordingId) } returns recording

        // When
        val result = fileManager.validateRecordingFile(recordingId)

        // Then
        assertTrue("Should return Missing", result is FileValidationResult.Missing)
        assertEquals("Should return correct path", nonExistentPath, (result as FileValidationResult.Missing).expectedPath)
    }

    @Test
    fun `validateRecordingFile should return Invalid for size mismatch`() = runTest {
        // Given
        val recordingId = "test123"
        val testFile = File.createTempFile("test", ".m4a")
        testFile.writeText("test audio data")
        
        val recording = createTestRecording(recordingId, "John Doe", "+1234567890")
            .copy(filePath = testFile.absolutePath, fileSize = 999999L) // Wrong size
        
        coEvery { mockRecordingDao.getRecordingById(recordingId) } returns recording

        // When
        val result = fileManager.validateRecordingFile(recordingId)

        // Then
        assertTrue("Should return Invalid", result is FileValidationResult.Invalid)
        assertTrue("Should mention size mismatch", (result as FileValidationResult.Invalid).reason.contains("size mismatch"))
        
        // Cleanup
        testFile.delete()
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