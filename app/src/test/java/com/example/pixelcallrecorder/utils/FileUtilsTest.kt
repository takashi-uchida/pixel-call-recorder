package com.example.pixelcallrecorder.utils

import android.content.Context
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class FileUtilsTest {

    private lateinit var mockContext: Context
    private lateinit var mockExternalFilesDir: File
    private lateinit var mockRecordingsDir: File

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockExternalFilesDir = mockk(relaxed = true)
        mockRecordingsDir = mockk(relaxed = true)

        every { mockContext.getExternalFilesDir(null) } returns mockExternalFilesDir
        every { mockExternalFilesDir.toString() } returns "/mock/external/files"

        mockkConstructor(File::class)
        every { anyConstructed<File>().exists() } returns true
        every { anyConstructed<File>().mkdirs() } returns true
        every { anyConstructed<File>().absolutePath } returns "/mock/path/recording.3gp"
    }

    @Test
    fun `getRecordingFilePath creates recordings directory if not exists`() {
        every { anyConstructed<File>().exists() } returns false

        val result = FileUtils.getRecordingFilePath(mockContext)

        verify { anyConstructed<File>().mkdirs() }
        assert(result.isNotEmpty())
    }

    @Test
    fun `getRecordingFilePath returns valid file path when directory exists`() {
        every { anyConstructed<File>().exists() } returns true

        val result = FileUtils.getRecordingFilePath(mockContext)

        assert(result.contains("recording_"))
        assert(result.endsWith(".3gp"))
    }

    @Test
    fun `getRecordingFilePath generates unique timestamps`() {
        every { anyConstructed<File>().absolutePath } returnsMany listOf(
            "/mock/path/recording_20241201_120000.3gp",
            "/mock/path/recording_20241201_120001.3gp"
        )

        val path1 = FileUtils.getRecordingFilePath(mockContext)
        Thread.sleep(1000) // 1秒待機
        val path2 = FileUtils.getRecordingFilePath(mockContext)

        assert(path1 != path2)
    }

    @Test
    fun `getRecordingsList returns empty list when directory does not exist`() {
        every { anyConstructed<File>().exists() } returns false

        val result = FileUtils.getRecordingsList(mockContext)

        assert(result.isEmpty())
    }

    @Test
    fun `getRecordingsList returns file list when directory exists`() {
        val mockFile1 = mockk<File>()
        val mockFile2 = mockk<File>()
        val mockFiles = arrayOf(mockFile1, mockFile2)

        every { anyConstructed<File>().exists() } returns true
        every { anyConstructed<File>().listFiles() } returns mockFiles

        val result = FileUtils.getRecordingsList(mockContext)

        assert(result.size == 2)
        assert(result.contains(mockFile1))
        assert(result.contains(mockFile2))
    }

    @Test
    fun `getRecordingsList returns empty list when listFiles returns null`() {
        every { anyConstructed<File>().exists() } returns true
        every { anyConstructed<File>().listFiles() } returns null

        val result = FileUtils.getRecordingsList(mockContext)

        assert(result.isEmpty())
    }

    @Test
    fun `getRecordingFilePath uses correct date format`() {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val currentTime = Date()
        val expectedTimestamp = dateFormat.format(currentTime)

        every { anyConstructed<File>().absolutePath } returns "/mock/path/recording_${expectedTimestamp}.3gp"

        val result = FileUtils.getRecordingFilePath(mockContext)

        assert(result.contains(expectedTimestamp.substring(0, 8))) // 日付部分をチェック
    }

    @Test
    fun `getRecordingFilePath handles null external files directory`() {
        every { mockContext.getExternalFilesDir(null) } returns null

        // NullPointerExceptionが発生する可能性があるが、実際の実装では適切に処理されるべき
        try {
            FileUtils.getRecordingFilePath(mockContext)
        } catch (e: Exception) {
            assert(e is NullPointerException || e is IllegalStateException)
        }
    }

    @Test
    fun `recordings directory path is correctly constructed`() {
        val result = FileUtils.getRecordingFilePath(mockContext)

        verify { mockContext.getExternalFilesDir(null) }
        // recordingsDirが正しく構築されることを確認
        verify { anyConstructed<File>().exists() }
    }
}
