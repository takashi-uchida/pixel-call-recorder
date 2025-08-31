package com.callrecorder.pixel.error

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.callrecorder.pixel.common.Result
import com.callrecorder.pixel.storage.FileManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ErrorRecoveryTest {

    private lateinit var context: Context
    private lateinit var fileManager: FileManager
    private lateinit var errorRecovery: ErrorRecovery

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        fileManager = mockk(relaxed = true)
        errorRecovery = ErrorRecovery(context, fileManager)

        // Mock static methods
        mockkStatic(Environment::class)
        mockkStatic(System::class)
        mockkConstructor(StatFs::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `attemptRecovery with RecordingError InsufficientStorage cleans old recordings`() = runTest {
        // Given
        val error = RecordingError.InsufficientStorage
        val recordingsDir = mockk<File>(relaxed = true)
        val oldFile1 = mockk<File>(relaxed = true)
        val oldFile2 = mockk<File>(relaxed = true)
        
        every { fileManager.getRecordingsDirectory() } returns recordingsDir
        every { recordingsDir.listFiles() } returns arrayOf(oldFile1, oldFile2)
        every { oldFile1.isFile } returns true
        every { oldFile1.extension } returns "mp3"
        every { oldFile1.lastModified() } returns 1000L
        every { oldFile1.delete() } returns true
        every { oldFile2.isFile } returns true
        every { oldFile2.extension } returns "wav"
        every { oldFile2.lastModified() } returns 2000L
        every { oldFile2.delete() } returns true
        
        // Mock StatFs for storage space check
        val statFs = mockk<StatFs>(relaxed = true)
        every { anyConstructed<StatFs>() } returns statFs
        every { statFs.availableBlocksLong } returns 1000L
        every { statFs.blockSizeLong } returns 1024L

        // When
        val result = errorRecovery.attemptRecovery(error)

        // Then
        assert(result.isSuccess)
        verify { oldFile1.delete() }
        verify { oldFile2.delete() }
    }

    @Test
    fun `attemptRecovery with StorageError DirectoryCreationFailed creates directories`() = runTest {
        // Given
        val error = StorageError.DirectoryCreationFailed
        val recordingsDir = mockk<File>(relaxed = true)
        
        every { fileManager.getRecordingsDirectory() } returns recordingsDir
        every { recordingsDir.exists() } returns false
        every { recordingsDir.mkdirs() } returns true

        // When
        val result = errorRecovery.attemptRecovery(error)

        // Then
        assert(result.isSuccess)
        verify { recordingsDir.mkdirs() }
    }

    @Test
    fun `attemptRecovery with StorageError StorageUnavailable retries with delay`() = runTest {
        // Given
        val error = StorageError.StorageUnavailable
        every { Environment.getExternalStorageState() } returns Environment.MEDIA_MOUNTED

        // When
        val result = errorRecovery.attemptRecovery(error, maxRetries = 1)

        // Then
        assert(result.isSuccess)
    }

    @Test
    fun `attemptRecovery with SystemError LowMemory triggers garbage collection`() = runTest {
        // Given
        val error = SystemError.LowMemory
        every { System.gc() } just Runs

        // When
        val result = errorRecovery.attemptRecovery(error)

        // Then
        assert(result.isSuccess)
        verify { System.gc() }
    }

    @Test
    fun `attemptRecovery with PermissionError returns error`() = runTest {
        // Given
        val error = PermissionError.MicrophonePermissionDenied

        // When
        val result = errorRecovery.attemptRecovery(error)

        // Then
        assert(result.isError)
        assert(result.errorOrNull() == error)
    }

    @Test
    fun `hasEnoughStorageSpace returns true when space available`() {
        // Given
        val recordingsDir = mockk<File>(relaxed = true)
        val statFs = mockk<StatFs>(relaxed = true)
        
        every { fileManager.getRecordingsDirectory() } returns recordingsDir
        every { recordingsDir.absolutePath } returns "/test/path"
        every { anyConstructed<StatFs>() } returns statFs
        every { statFs.availableBlocksLong } returns 1000L
        every { statFs.blockSizeLong } returns 1024L * 1024L // 1MB blocks

        // When
        val hasSpace = errorRecovery.hasEnoughStorageSpace(50 * 1024 * 1024L) // 50MB required

        // Then
        assert(hasSpace) // 1000MB available > 50MB required
    }

    @Test
    fun `hasEnoughStorageSpace returns false when insufficient space`() {
        // Given
        val recordingsDir = mockk<File>(relaxed = true)
        val statFs = mockk<StatFs>(relaxed = true)
        
        every { fileManager.getRecordingsDirectory() } returns recordingsDir
        every { recordingsDir.absolutePath } returns "/test/path"
        every { anyConstructed<StatFs>() } returns statFs
        every { statFs.availableBlocksLong } returns 10L
        every { statFs.blockSizeLong } returns 1024L * 1024L // 1MB blocks

        // When
        val hasSpace = errorRecovery.hasEnoughStorageSpace(50 * 1024 * 1024L) // 50MB required

        // Then
        assert(!hasSpace) // 10MB available < 50MB required
    }

    @Test
    fun `getStorageStats returns correct statistics`() {
        // Given
        val recordingsDir = mockk<File>(relaxed = true)
        val statFs = mockk<StatFs>(relaxed = true)
        val file1 = mockk<File>(relaxed = true)
        val file2 = mockk<File>(relaxed = true)
        
        every { fileManager.getRecordingsDirectory() } returns recordingsDir
        every { recordingsDir.absolutePath } returns "/test/path"
        every { recordingsDir.listFiles() } returns arrayOf(file1, file2)
        
        every { file1.isFile } returns true
        every { file1.extension } returns "mp3"
        every { file1.length() } returns 1024L * 1024L // 1MB
        
        every { file2.isFile } returns true
        every { file2.extension } returns "wav"
        every { file2.length() } returns 2 * 1024L * 1024L // 2MB
        
        every { anyConstructed<StatFs>() } returns statFs
        every { statFs.totalBytes } returns 100L * 1024L * 1024L // 100MB total
        every { statFs.availableBlocksLong } returns 50L
        every { statFs.blockSizeLong } returns 1024L * 1024L // 1MB blocks

        // When
        val stats = errorRecovery.getStorageStats()

        // Then
        assert(stats.totalSpace == 100L * 1024L * 1024L)
        assert(stats.availableSpace == 50L * 1024L * 1024L)
        assert(stats.usedSpace == 50L * 1024L * 1024L)
        assert(stats.recordingsSize == 3L * 1024L * 1024L) // 1MB + 2MB
        assert(stats.recordingsCount == 2)
        assert(stats.usagePercentage == 50f)
        assert(stats.recordingsPercentage == 3f)
    }

    @Test
    fun `cleanupOldRecordings deletes oldest files when storage low`() = runTest {
        // Given
        val recordingsDir = mockk<File>(relaxed = true)
        val statFs = mockk<StatFs>(relaxed = true)
        val oldFile = mockk<File>(relaxed = true)
        val newFile = mockk<File>(relaxed = true)
        
        every { fileManager.getRecordingsDirectory() } returns recordingsDir
        every { recordingsDir.absolutePath } returns "/test/path"
        every { recordingsDir.listFiles() } returns arrayOf(oldFile, newFile)
        
        every { oldFile.isFile } returns true
        every { oldFile.extension } returns "mp3"
        every { oldFile.lastModified() } returns 1000L
        every { oldFile.delete() } returns true
        
        every { newFile.isFile } returns true
        every { newFile.extension } returns "wav"
        every { newFile.lastModified() } returns 2000L
        every { newFile.delete() } returns true
        
        every { anyConstructed<StatFs>() } returns statFs
        every { statFs.availableBlocksLong } returns 50L // 50MB available
        every { statFs.blockSizeLong } returns 1024L * 1024L

        // When
        val result = errorRecovery.attemptRecovery(RecordingError.InsufficientStorage)

        // Then
        assert(result.isSuccess)
        verify { oldFile.delete() }
        verify { newFile.delete() }
    }

    @Test
    fun `createRecordingDirectories creates temp subdirectory`() = runTest {
        // Given
        val error = StorageError.DirectoryCreationFailed
        val recordingsDir = mockk<File>(relaxed = true)
        val tempDir = mockk<File>(relaxed = true)
        
        every { fileManager.getRecordingsDirectory() } returns recordingsDir
        every { recordingsDir.exists() } returns true
        every { recordingsDir.mkdirs() } returns true
        
        mockkConstructor(File::class)
        every { anyConstructed<File>() } returns tempDir
        every { tempDir.exists() } returns false
        every { tempDir.mkdirs() } returns true

        // When
        val result = errorRecovery.attemptRecovery(error)

        // Then
        assert(result.isSuccess)
        verify { tempDir.mkdirs() }
    }
}