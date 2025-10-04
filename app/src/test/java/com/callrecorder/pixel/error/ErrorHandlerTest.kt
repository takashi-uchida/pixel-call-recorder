package com.callrecorder.pixel.error

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.callrecorder.pixel.common.Result
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ErrorHandlerTest {

    private lateinit var context: Context
    private lateinit var errorHandler: ErrorHandler

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        errorHandler = ErrorHandler(context)
        
        // Mock static methods
        mockkStatic(Toast::class)
        every { Toast.makeText(any(), any<String>(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `handleError with PermissionError shows permission dialog`() {
        // Given
        val error = PermissionError.MicrophonePermissionDenied
        val onRetry = mockk<() -> Unit>(relaxed = true)
        val onCancel = mockk<() -> Unit>(relaxed = true)

        // Mock AlertDialog.Builder
        val dialogBuilder = mockk<AlertDialog.Builder>(relaxed = true)
        val dialog = mockk<AlertDialog>(relaxed = true)
        
        every { dialogBuilder.setTitle(any<String>()) } returns dialogBuilder
        every { dialogBuilder.setMessage(any<String>()) } returns dialogBuilder
        every { dialogBuilder.setPositiveButton(any<String>(), any()) } returns dialogBuilder
        every { dialogBuilder.setNegativeButton(any<String>(), any()) } returns dialogBuilder
        every { dialogBuilder.setNeutralButton(any<String>(), any()) } returns dialogBuilder
        every { dialogBuilder.setCancelable(any()) } returns dialogBuilder
        every { dialogBuilder.show() } returns dialog

        mockkConstructor(AlertDialog.Builder::class)
        every { anyConstructed<AlertDialog.Builder>() } returns dialogBuilder

        // When
        errorHandler.handleError(error, showDialog = true, onRetry = onRetry, onCancel = onCancel)

        // Then
        verify { dialogBuilder.setTitle("権限が必要です") }
        verify { dialogBuilder.setMessage(error.userMessage) }
        verify { dialogBuilder.show() }
    }

    @Test
    fun `handleError with RecordingError InsufficientStorage shows storage dialog`() {
        // Given
        val error = RecordingError.InsufficientStorage
        val onRetry = mockk<() -> Unit>(relaxed = true)
        val onCancel = mockk<() -> Unit>(relaxed = true)

        // Mock AlertDialog.Builder
        val dialogBuilder = mockk<AlertDialog.Builder>(relaxed = true)
        val dialog = mockk<AlertDialog>(relaxed = true)
        
        every { dialogBuilder.setTitle(any<String>()) } returns dialogBuilder
        every { dialogBuilder.setMessage(any<String>()) } returns dialogBuilder
        every { dialogBuilder.setPositiveButton(any<String>(), any()) } returns dialogBuilder
        every { dialogBuilder.setNegativeButton(any<String>(), any()) } returns dialogBuilder
        every { dialogBuilder.setNeutralButton(any<String>(), any()) } returns dialogBuilder
        every { dialogBuilder.show() } returns dialog

        mockkConstructor(AlertDialog.Builder::class)
        every { anyConstructed<AlertDialog.Builder>() } returns dialogBuilder

        // When
        errorHandler.handleError(error, showDialog = true, onRetry = onRetry, onCancel = onCancel)

        // Then
        verify { dialogBuilder.setTitle("ストレージ不足") }
        verify { dialogBuilder.show() }
    }

    @Test
    fun `handleError with showDialog false shows toast`() {
        // Given
        val error = RecordingError.AudioSourceNotAvailable

        // When
        errorHandler.handleError(error, showDialog = false)

        // Then
        verify { Toast.makeText(context, error.userMessage, Toast.LENGTH_LONG) }
    }

    @Test
    fun `handleError with ValidationError shows validation dialog`() {
        // Given
        val error = ValidationError.InvalidInput("testField")

        // Mock AlertDialog.Builder
        val dialogBuilder = mockk<AlertDialog.Builder>(relaxed = true)
        val dialog = mockk<AlertDialog>(relaxed = true)
        
        every { dialogBuilder.setTitle(any<String>()) } returns dialogBuilder
        every { dialogBuilder.setMessage(any<String>()) } returns dialogBuilder
        every { dialogBuilder.setPositiveButton(any<String>(), any()) } returns dialogBuilder
        every { dialogBuilder.show() } returns dialog

        mockkConstructor(AlertDialog.Builder::class)
        every { anyConstructed<AlertDialog.Builder>() } returns dialogBuilder

        // When
        errorHandler.handleError(error, showDialog = true)

        // Then
        verify { dialogBuilder.setTitle("入力エラー") }
        verify { dialogBuilder.setMessage(error.userMessage) }
        verify { dialogBuilder.show() }
    }

    @Test
    fun `handleResultError with Success returns data`() = runTest {
        // Given
        val successResult = Result.success("test data")
        val errorHandler = ErrorHandler(context)

        // When
        val result = ErrorHandler.handleResultError(
            result = successResult,
            errorHandler = errorHandler,
            showDialog = false
        )

        // Then
        assert(result == "test data")
    }

    @Test
    fun `handleResultError with Error returns null and handles error`() = runTest {
        // Given
        val error = RecordingError.AudioSourceNotAvailable
        val errorResult = Result.error<String>(error)
        val errorHandler = spyk(ErrorHandler(context))

        // When
        val result = ErrorHandler.handleResultError(
            result = errorResult,
            errorHandler = errorHandler,
            showDialog = false
        )

        // Then
        assert(result == null)
        verify { errorHandler.handleError(error, false, null, null) }
    }

    @Test
    fun `Result handleWith extension function works correctly`() {
        // Given
        val error = RecordingError.AudioProcessingFailed
        val errorResult = Result.error<String>(error)
        val errorHandler = spyk(ErrorHandler(context))

        // When
        val result = errorResult.handleWith(errorHandler, showDialog = false)

        // Then
        assert(result == null)
        verify { errorHandler.handleError(error, false, null, null) }
    }

    @Test
    fun `openAppSettings creates correct intent`() {
        // Given
        val error = PermissionError.MicrophonePermissionDenied
        every { context.packageName } returns "com.test.package"
        every { context.startActivity(any()) } just Runs

        // Mock AlertDialog to capture the positive button click
        val dialogBuilder = mockk<AlertDialog.Builder>(relaxed = true)
        val dialog = mockk<AlertDialog>(relaxed = true)
        
        var positiveButtonListener: ((Any, Int) -> Unit)? = null
        
        every { dialogBuilder.setTitle(any<String>()) } returns dialogBuilder
        every { dialogBuilder.setMessage(any<String>()) } returns dialogBuilder
        every { dialogBuilder.setPositiveButton(any<String>(), capture(slot<(Any, Int) -> Unit>())) } answers {
            positiveButtonListener = secondArg()
            dialogBuilder
        }
        every { dialogBuilder.setNegativeButton(any<String>(), any()) } returns dialogBuilder
        every { dialogBuilder.setNeutralButton(any<String>(), any()) } returns dialogBuilder
        every { dialogBuilder.setCancelable(any()) } returns dialogBuilder
        every { dialogBuilder.show() } returns dialog

        mockkConstructor(AlertDialog.Builder::class)
        every { anyConstructed<AlertDialog.Builder>() } returns dialogBuilder

        // When
        errorHandler.handleError(error, showDialog = true)
        positiveButtonListener?.invoke(mockk(), 0)

        // Then
        verify {
            context.startActivity(match<Intent> { intent ->
                intent.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS &&
                intent.data?.toString()?.contains("com.test.package") == true
            })
        }
    }
}