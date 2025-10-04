package com.callrecorder.pixel.storage

import android.content.Context
import android.os.StatFs
import com.callrecorder.pixel.data.dao.RecordingDao
import com.callrecorder.pixel.data.model.AudioQuality
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for storage management functionality.
 * Tests storage capacity checking, space calculations, and cleanup operations.
 */
class StorageManagerTest {

    @MockK
    private lateinit var mockContext: Context

    @MockK
    private lateinit var mockRecordingDao: RecordingDao

    private lateinit var fileManager: FileManagerImpl

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        // Mock external files directory
        val testDir = createTempDir("test_recordings")
        every { mockContext.getExternalFilesDir(any()) } returns testDir.parentFile
        
        fileManager = FileManagerImpl(mockContext, mockRecordingDao)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `getAvailableStorage should return correct available space`() = runTest {
        // Given
        val expectedAvailableBytes = 1024L * 1024L * 1024L // 1GB
        val blockSize = 4096L // 4KB blocks
        val availableBlocks = expectedAvailableBytes / blockSize

        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns availableBlocks
        every { anyConstructed<StatFs>().blockSizeLong } returns blockSize

        // When
        val result = fileManager.getAvailableStorage()

        // Then
        assertEquals("Should return correct available storage", expectedAvailableBytes, result)
    }

    @Test
    fun `getAvailableStorage should handle StatFs errors gracefully`() = runTest {
        // Given
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } throws SecurityException("Access denied")

        // When
        val result = fileManager.getAvailableStorage()

        // Then
        assertEquals("Should return 0 on error", 0L, result)
    }

    @Test
    fun `hasEnoughSpace should return true when sufficient space available`() = runTest {
        // Given
        val estimatedSize = 50L * 1024L * 1024L // 50MB
        val availableSpace = 200L * 1024L * 1024L // 200MB
        val blockSize = 4096L
        val availableBlocks = availableSpace / blockSize

        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns availableBlocks
        every { anyConstructed<StatFs>().blockSizeLong } returns blockSize

        // When
        val result = fileManager.hasEnoughSpace(estimatedSize)

        // Then
        assertTrue("Should return true when enough space available", result)
    }

    @Test
    fun `hasEnoughSpace should return false when insufficient space`() = runTest {
        // Given
        val estimatedSize = 150L * 1024L * 1024L // 150MB
        val availableSpace = 50L * 1024L * 1024L // 50MB (less than estimated + buffer)
        val blockSize = 4096L
        val availableBlocks = availableSpace / blockSize

        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns availableBlocks
        every { anyConstructed<StatFs>().blockSizeLong } returns blockSize

        // When
        val result = fileManager.hasEnoughSpace(estimatedSize)

        // Then
        assertFalse("Should return false when insufficient space", result)
    }

    @Test
    fun `hasEnoughSpace should account for minimum free space buffer`() = runTest {
        // Given
        val estimatedSize = 50L * 1024L * 1024L // 50MB
        val minFreeSpace = 100L * 1024L * 1024L // 100MB buffer
        val availableSpace = 140L * 1024L * 1024L // 140MB (less than estimated + buffer)
        val blockSize = 4096L
        val availableBlocks = availableSpace / blockSize

        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns availableBlocks
        every { anyConstructed<StatFs>().blockSizeLong } returns blockSize

        // When
        val result = fileManager.hasEnoughSpace(estimatedSize)

        // Then
        assertFalse("Should return false when not enough space including buffer", result)
    }

    @Test
    fun `estimateRecordingSize should calculate size based on audio quality and duration`() {
        // Test different audio qualities
        val durationMinutes = 5L
        
        // High quality: 48kHz, 128kbps, 2 channels
        val highQualitySize = AudioQuality.HIGH_QUALITY.getEstimatedFileSizePerMinute() * durationMinutes
        assertEquals("High quality should be largest", 4800000L, highQualitySize) // 128kbps * 60s / 8 bits per byte * 5 minutes
        
        // Standard quality: 44.1kHz, 64kbps, 1 channel
        val standardSize = AudioQuality.STANDARD.getEstimatedFileSizePerMinute() * durationMinutes
        assertEquals("Standard quality should be medium", 2400000L, standardSize) // 64kbps * 60s / 8 * 5
        
        // Space saving: 22kHz, 32kbps, 1 channel
        val spaceSavingSize = AudioQuality.SPACE_SAVING.getEstimatedFileSizePerMinute() * durationMinutes
        assertEquals("Space saving should be smallest", 1200000L, spaceSavingSize) // 32kbps * 60s / 8 * 5
    }

    @Test
    fun `getTotalStorageUsed should return sum from database`() = runTest {
        // Given
        val expectedTotalSize = 500L * 1024L * 1024L // 500MB
        
        coEvery { mockRecordingDao.getTotalFileSize() } returns expectedTotalSize

        // When
        val result = fileManager.getTotalStorageUsed()

        // Then
        assertEquals("Should return total from database", expectedTotalSize, result)
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
    fun `getTotalStorageUsed should handle database errors gracefully`() = runTest {
        // Given
        coEvery { mockRecordingDao.getTotalFileSize() } throws Exception("Database error")

        // When
        val result = fileManager.getTotalStorageUsed()

        // Then
        assertEquals("Should return 0 on database error", 0L, result)
    }

    @Test
    fun `storage calculations should be consistent`() = runTest {
        // Given
        val totalUsed = 300L * 1024L * 1024L // 300MB
        val totalAvailable = 1000L * 1024L * 1024L // 1GB
        val estimatedNewRecording = 50L * 1024L * 1024L // 50MB

        coEvery { mockRecordingDao.getTotalFileSize() } returns totalUsed

        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns totalAvailable / 4096L
        every { anyConstructed<StatFs>().blockSizeLong } returns 4096L

        // When
        val usedSpace = fileManager.getTotalStorageUsed()
        val availableSpace = fileManager.getAvailableStorage()
        val hasSpace = fileManager.hasEnoughSpace(estimatedNewRecording)

        // Then
        assertEquals("Used space should match", totalUsed, usedSpace)
        assertEquals("Available space should match", totalAvailable, availableSpace)
        assertTrue("Should have enough space for new recording", hasSpace)
    }

    @Test
    fun `storage warning thresholds should work correctly`() = runTest {
        // Test scenario where we're close to storage limit
        val lowAvailableSpace = 80L * 1024L * 1024L // 80MB available
        val largeRecordingEstimate = 60L * 1024L * 1024L // 60MB recording
        
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns lowAvailableSpace / 4096L
        every { anyConstructed<StatFs>().blockSizeLong } returns 4096L

        // When
        val hasSpace = fileManager.hasEnoughSpace(largeRecordingEstimate)

        // Then
        assertFalse("Should not have enough space when close to limit", hasSpace)
    }

    @Test
    fun `multiple storage checks should be consistent`() = runTest {
        // Given
        val availableSpace = 500L * 1024L * 1024L // 500MB
        val blockSize = 4096L
        
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns availableSpace / blockSize
        every { anyConstructed<StatFs>().blockSizeLong } returns blockSize

        // When - Multiple calls
        val result1 = fileManager.getAvailableStorage()
        val result2 = fileManager.getAvailableStorage()
        val result3 = fileManager.getAvailableStorage()

        // Then
        assertEquals("First call should return expected value", availableSpace, result1)
        assertEquals("Second call should be consistent", result1, result2)
        assertEquals("Third call should be consistent", result1, result3)
    }
}