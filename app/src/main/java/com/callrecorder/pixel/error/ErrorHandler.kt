package com.callrecorder.pixel.error

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.callrecorder.pixel.common.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Centralized error handler that provides consistent error handling,
 * recovery mechanisms, and user feedback across the application.
 */
class ErrorHandler(private val context: Context) {

    /**
     * Handles errors with appropriate user feedback and recovery options
     */
    fun handleError(
        error: CallRecorderError,
        showDialog: Boolean = true,
        onRetry: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        when (error) {
            is PermissionError -> handlePermissionError(error, showDialog, onRetry, onCancel)
            is RecordingError -> handleRecordingError(error, showDialog, onRetry, onCancel)
            is StorageError -> handleStorageError(error, showDialog, onRetry, onCancel)
            is DatabaseError -> handleDatabaseError(error, showDialog, onRetry, onCancel)
            is NetworkError -> handleNetworkError(error, showDialog, onRetry, onCancel)
            is SystemError -> handleSystemError(error, showDialog, onRetry, onCancel)
            is ValidationError -> handleValidationError(error, showDialog)
        }
    }

    /**
     * Handles permission-related errors with recovery options
     */
    private fun handlePermissionError(
        error: PermissionError,
        showDialog: Boolean,
        onRetry: (() -> Unit)?,
        onCancel: (() -> Unit)?
    ) {
        if (showDialog) {
            AlertDialog.Builder(context)
                .setTitle("権限が必要です")
                .setMessage(error.userMessage)
                .setPositiveButton("設定を開く") { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton("キャンセル") { _, _ ->
                    onCancel?.invoke()
                }
                .setNeutralButton("再試行") { _, _ ->
                    onRetry?.invoke()
                }
                .setCancelable(false)
                .show()
        } else {
            showToast(error.userMessage)
        }
    }

    /**
     * Handles recording-related errors with recovery mechanisms
     */
    private fun handleRecordingError(
        error: RecordingError,
        showDialog: Boolean,
        onRetry: (() -> Unit)?,
        onCancel: (() -> Unit)?
    ) {
        when (error) {
            is RecordingError.AudioSourceNotAvailable -> {
                if (showDialog) {
                    AlertDialog.Builder(context)
                        .setTitle("音声ソースエラー")
                        .setMessage("${error.userMessage}\n\n他のアプリを終了してから再試行してください。")
                        .setPositiveButton("再試行") { _, _ ->
                            onRetry?.invoke()
                        }
                        .setNegativeButton("キャンセル") { _, _ ->
                            onCancel?.invoke()
                        }
                        .show()
                } else {
                    showToast(error.userMessage)
                }
            }
            
            is RecordingError.InsufficientStorage -> {
                if (showDialog) {
                    AlertDialog.Builder(context)
                        .setTitle("ストレージ不足")
                        .setMessage("${error.userMessage}\n\n不要なファイルを削除するか、ストレージ設定を開きますか？")
                        .setPositiveButton("ストレージ設定") { _, _ ->
                            openStorageSettings()
                        }
                        .setNegativeButton("キャンセル") { _, _ ->
                            onCancel?.invoke()
                        }
                        .setNeutralButton("再試行") { _, _ ->
                            onRetry?.invoke()
                        }
                        .show()
                } else {
                    showToast(error.userMessage)
                }
            }
            
            else -> {
                if (showDialog) {
                    AlertDialog.Builder(context)
                        .setTitle("録音エラー")
                        .setMessage(error.userMessage)
                        .setPositiveButton("再試行") { _, _ ->
                            onRetry?.invoke()
                        }
                        .setNegativeButton("キャンセル") { _, _ ->
                            onCancel?.invoke()
                        }
                        .show()
                } else {
                    showToast(error.userMessage)
                }
            }
        }
    }

    /**
     * Handles storage-related errors
     */
    private fun handleStorageError(
        error: StorageError,
        showDialog: Boolean,
        onRetry: (() -> Unit)?,
        onCancel: (() -> Unit)?
    ) {
        when (error) {
            is StorageError.StorageUnavailable -> {
                if (showDialog) {
                    AlertDialog.Builder(context)
                        .setTitle("ストレージエラー")
                        .setMessage("${error.userMessage}\n\nSDカードが正しく挿入されているか確認してください。")
                        .setPositiveButton("再試行") { _, _ ->
                            onRetry?.invoke()
                        }
                        .setNegativeButton("キャンセル") { _, _ ->
                            onCancel?.invoke()
                        }
                        .show()
                } else {
                    showToast(error.userMessage)
                }
            }
            
            else -> {
                if (showDialog) {
                    AlertDialog.Builder(context)
                        .setTitle("ファイルエラー")
                        .setMessage(error.userMessage)
                        .setPositiveButton("OK") { _, _ ->
                            onCancel?.invoke()
                        }
                        .show()
                } else {
                    showToast(error.userMessage)
                }
            }
        }
    }

    /**
     * Handles database-related errors
     */
    private fun handleDatabaseError(
        error: DatabaseError,
        showDialog: Boolean,
        onRetry: (() -> Unit)?,
        onCancel: (() -> Unit)?
    ) {
        when (error) {
            is DatabaseError.DatabaseCorrupted -> {
                if (showDialog) {
                    AlertDialog.Builder(context)
                        .setTitle("データベースエラー")
                        .setMessage("${error.userMessage}\n\nアプリのデータをリセットしますか？")
                        .setPositiveButton("リセット") { _, _ ->
                            // データベースリセット処理をここに実装
                            onRetry?.invoke()
                        }
                        .setNegativeButton("キャンセル") { _, _ ->
                            onCancel?.invoke()
                        }
                        .show()
                } else {
                    showToast(error.userMessage)
                }
            }
            
            else -> {
                if (showDialog) {
                    AlertDialog.Builder(context)
                        .setTitle("データエラー")
                        .setMessage(error.userMessage)
                        .setPositiveButton("再試行") { _, _ ->
                            onRetry?.invoke()
                        }
                        .setNegativeButton("キャンセル") { _, _ ->
                            onCancel?.invoke()
                        }
                        .show()
                } else {
                    showToast(error.userMessage)
                }
            }
        }
    }

    /**
     * Handles network-related errors
     */
    private fun handleNetworkError(
        error: NetworkError,
        showDialog: Boolean,
        onRetry: (() -> Unit)?,
        onCancel: (() -> Unit)?
    ) {
        if (showDialog) {
            AlertDialog.Builder(context)
                .setTitle("ネットワークエラー")
                .setMessage("${error.userMessage}\n\nネットワーク接続を確認してください。")
                .setPositiveButton("再試行") { _, _ ->
                    onRetry?.invoke()
                }
                .setNegativeButton("キャンセル") { _, _ ->
                    onCancel?.invoke()
                }
                .setNeutralButton("設定") { _, _ ->
                    openNetworkSettings()
                }
                .show()
        } else {
            showToast(error.userMessage)
        }
    }

    /**
     * Handles system-related errors
     */
    private fun handleSystemError(
        error: SystemError,
        showDialog: Boolean,
        onRetry: (() -> Unit)?,
        onCancel: (() -> Unit)?
    ) {
        when (error) {
            is SystemError.LowMemory -> {
                if (showDialog) {
                    AlertDialog.Builder(context)
                        .setTitle("メモリ不足")
                        .setMessage("${error.userMessage}\n\n他のアプリを終了してから再試行してください。")
                        .setPositiveButton("再試行") { _, _ ->
                            onRetry?.invoke()
                        }
                        .setNegativeButton("キャンセル") { _, _ ->
                            onCancel?.invoke()
                        }
                        .show()
                } else {
                    showToast(error.userMessage)
                }
            }
            
            else -> {
                if (showDialog) {
                    AlertDialog.Builder(context)
                        .setTitle("システムエラー")
                        .setMessage(error.userMessage)
                        .setPositiveButton("OK") { _, _ ->
                            onCancel?.invoke()
                        }
                        .show()
                } else {
                    showToast(error.userMessage)
                }
            }
        }
    }

    /**
     * Handles validation errors
     */
    private fun handleValidationError(
        error: ValidationError,
        showDialog: Boolean
    ) {
        if (showDialog) {
            AlertDialog.Builder(context)
                .setTitle("入力エラー")
                .setMessage(error.userMessage)
                .setPositiveButton("OK", null)
                .show()
        } else {
            showToast(error.userMessage)
        }
    }

    /**
     * Shows a toast message
     */
    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Opens the app settings screen
     */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Opens the storage settings screen
     */
    private fun openStorageSettings() {
        val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Opens the network settings screen
     */
    private fun openNetworkSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    companion object {
        /**
         * Handles Result errors with coroutine support
         */
        suspend fun <T> handleResultError(
            result: Result<T>,
            errorHandler: ErrorHandler,
            showDialog: Boolean = true,
            onRetry: (suspend () -> Unit)? = null,
            onCancel: (() -> Unit)? = null
        ): T? = withContext(Dispatchers.Main) {
            when (result) {
                is Result.Success -> result.data
                is Result.Error -> {
                    errorHandler.handleError(
                        error = result.error,
                        showDialog = showDialog,
                        onRetry = onRetry?.let { retry ->
                            {
                                CoroutineScope(Dispatchers.Main).launch {
                                    retry()
                                }
                            }
                        },
                        onCancel = onCancel
                    )
                    null
                }
                is Result.Loading -> null
            }
        }

        /**
         * Extension function for Result to handle errors easily
         */
        fun <T> Result<T>.handleWith(
            errorHandler: ErrorHandler,
            showDialog: Boolean = true,
            onRetry: (() -> Unit)? = null,
            onCancel: (() -> Unit)? = null
        ): T? {
            return when (this) {
                is Result.Success -> data
                is Result.Error -> {
                    errorHandler.handleError(
                        error = error,
                        showDialog = showDialog,
                        onRetry = onRetry,
                        onCancel = onCancel
                    )
                    null
                }
                is Result.Loading -> null
            }
        }
    }
}