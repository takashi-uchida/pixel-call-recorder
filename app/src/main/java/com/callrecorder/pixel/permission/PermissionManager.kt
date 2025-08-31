package com.callrecorder.pixel.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.callrecorder.pixel.error.PermissionError

/**
 * Manages all permission-related operations for the call recorder app.
 * Handles checking, requesting, and explaining permissions to users.
 */
class PermissionManager(private val context: Context) {

    companion object {
        // Permission request codes
        const val REQUEST_MICROPHONE_PERMISSION = 1001
        const val REQUEST_PHONE_STATE_PERMISSION = 1002
        const val REQUEST_STORAGE_PERMISSION = 1003
        const val REQUEST_NOTIFICATION_PERMISSION = 1004
        const val REQUEST_ALL_PERMISSIONS = 1000

        // Required permissions for different Android versions
        private val REQUIRED_PERMISSIONS_BASE = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        )

        private val STORAGE_PERMISSIONS_LEGACY = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        private val NOTIFICATION_PERMISSIONS_ANDROID_13 = arrayOf(
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    /**
     * Data class representing the current permission status
     */
    data class PermissionStatus(
        val hasMicrophonePermission: Boolean,
        val hasPhoneStatePermission: Boolean,
        val hasStoragePermission: Boolean,
        val hasNotificationPermission: Boolean,
        val allRequiredPermissionsGranted: Boolean
    )

    /**
     * Checks if microphone permission is granted
     */
    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Backward-compatible alias expected by some tests
    fun hasRecordAudioPermission(): Boolean = hasMicrophonePermission()

    /**
     * Checks if phone state permission is granted
     */
    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if storage permission is granted
     * For Android 10+ (API 29+), we use scoped storage so no explicit permission needed
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses scoped storage, no explicit permission needed for app-specific directories
            true
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Not required for older versions
            true
        }
    }

    /**
     * Gets the current status of all permissions
     */
    fun getPermissionStatus(): PermissionStatus {
        val micPermission = hasMicrophonePermission()
        val phonePermission = hasPhoneStatePermission()
        val storagePermission = hasStoragePermission()
        val notificationPermission = hasNotificationPermission()

        return PermissionStatus(
            hasMicrophonePermission = micPermission,
            hasPhoneStatePermission = phonePermission,
            hasStoragePermission = storagePermission,
            hasNotificationPermission = notificationPermission,
            allRequiredPermissionsGranted = micPermission && phonePermission && storagePermission && notificationPermission
        )
    }

    /**
     * Checks if all required permissions are granted
     */
    fun hasAllRequiredPermissions(): Boolean {
        return getPermissionStatus().allRequiredPermissionsGranted
    }

    /**
     * Gets the list of permissions that need to be requested
     */
    fun getMissingPermissions(): List<String> {
        val missingPermissions = mutableListOf<String>()

        if (!hasMicrophonePermission()) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (!hasPhoneStatePermission()) {
            missingPermissions.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (!hasStoragePermission() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            missingPermissions.addAll(STORAGE_PERMISSIONS_LEGACY)
        }

        if (!hasNotificationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            missingPermissions.addAll(NOTIFICATION_PERMISSIONS_ANDROID_13)
        }

        return missingPermissions
    }

    /**
     * Requests microphone permission
     */
    fun requestMicrophonePermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_MICROPHONE_PERMISSION
        )
    }

    /**
     * Requests phone state permission
     */
    fun requestPhoneStatePermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.READ_PHONE_STATE),
            REQUEST_PHONE_STATE_PERMISSION
        )
    }

    /**
     * Requests storage permission (for Android < 10)
     */
    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                activity,
                STORAGE_PERMISSIONS_LEGACY,
                REQUEST_STORAGE_PERMISSION
            )
        }
    }

    /**
     * Requests notification permission (for Android 13+)
     */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                NOTIFICATION_PERMISSIONS_ANDROID_13,
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }

    /**
     * Requests all missing permissions at once
     */
    fun requestAllMissingPermissions(activity: Activity) {
        val missingPermissions = getMissingPermissions()
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions.toTypedArray(),
                REQUEST_ALL_PERMISSIONS
            )
        }
    }

    /**
     * Checks if we should show rationale for a specific permission
     */
    fun shouldShowPermissionRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    /**
     * Gets explanation message for a specific permission
     */
    fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> 
                "通話を録音するためにマイクへのアクセスが必要です。この権限により、高品質な音声録音が可能になります。"
            
            Manifest.permission.READ_PHONE_STATE -> 
                "通話の開始と終了を検出するために電話状態へのアクセスが必要です。この権限により、自動録音機能が利用できます。"
            
            Manifest.permission.WRITE_EXTERNAL_STORAGE, 
            Manifest.permission.READ_EXTERNAL_STORAGE -> 
                "録音ファイルを保存するためにストレージへのアクセスが必要です。この権限により、録音データを安全に保存できます。"
            
            Manifest.permission.POST_NOTIFICATIONS -> 
                "録音状態をお知らせするために通知の許可が必要です。この権限により、録音中の状態を確認できます。"
            
            else -> "アプリの正常な動作のためにこの権限が必要です。"
        }
    }

    /**
     * Opens the app settings screen for manual permission management
     */
    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(intent)
    }

    /**
     * Handles permission request results
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onPermissionGranted: (String) -> Unit = {},
        onPermissionDenied: (String, PermissionError) -> Unit = { _, _ -> }
    ) {
        if (permissions.isEmpty() || grantResults.isEmpty()) return

        when (requestCode) {
            REQUEST_MICROPHONE_PERMISSION -> {
                handleSinglePermissionResult(
                    Manifest.permission.RECORD_AUDIO,
                    permissions,
                    grantResults,
                    PermissionError.MicrophonePermissionDenied,
                    onPermissionGranted,
                    onPermissionDenied
                )
            }
            
            REQUEST_PHONE_STATE_PERMISSION -> {
                handleSinglePermissionResult(
                    Manifest.permission.READ_PHONE_STATE,
                    permissions,
                    grantResults,
                    PermissionError.PhoneStatePermissionDenied,
                    onPermissionGranted,
                    onPermissionDenied
                )
            }
            
            REQUEST_STORAGE_PERMISSION -> {
                handleStoragePermissionResult(permissions, grantResults, onPermissionGranted, onPermissionDenied)
            }
            
            REQUEST_NOTIFICATION_PERMISSION -> {
                handleSinglePermissionResult(
                    Manifest.permission.POST_NOTIFICATIONS,
                    permissions,
                    grantResults,
                    PermissionError.NotificationPermissionDenied,
                    onPermissionGranted,
                    onPermissionDenied
                )
            }
            
            REQUEST_ALL_PERMISSIONS -> {
                handleMultiplePermissionResults(permissions, grantResults, onPermissionGranted, onPermissionDenied)
            }
        }
    }

    private fun handleSinglePermissionResult(
        targetPermission: String,
        permissions: Array<out String>,
        grantResults: IntArray,
        error: PermissionError,
        onPermissionGranted: (String) -> Unit,
        onPermissionDenied: (String, PermissionError) -> Unit
    ) {
        val index = permissions.indexOf(targetPermission)
        if (index >= 0) {
            if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted(targetPermission)
            } else {
                onPermissionDenied(targetPermission, error)
            }
        }
    }

    private fun handleStoragePermissionResult(
        permissions: Array<out String>,
        grantResults: IntArray,
        onPermissionGranted: (String) -> Unit,
        onPermissionDenied: (String, PermissionError) -> Unit
    ) {
        var allStoragePermissionsGranted = true
        
        for (i in permissions.indices) {
            if (permissions[i] in STORAGE_PERMISSIONS_LEGACY) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    onPermissionGranted(permissions[i])
                } else {
                    allStoragePermissionsGranted = false
                    onPermissionDenied(permissions[i], PermissionError.StoragePermissionDenied)
                }
            }
        }
    }

    private fun handleMultiplePermissionResults(
        permissions: Array<out String>,
        grantResults: IntArray,
        onPermissionGranted: (String) -> Unit,
        onPermissionDenied: (String, PermissionError) -> Unit
    ) {
        for (i in permissions.indices) {
            val permission = permissions[i]
            val granted = grantResults[i] == PackageManager.PERMISSION_GRANTED
            
            if (granted) {
                onPermissionGranted(permission)
            } else {
                val error = when (permission) {
                    Manifest.permission.RECORD_AUDIO -> PermissionError.MicrophonePermissionDenied
                    Manifest.permission.READ_PHONE_STATE -> PermissionError.PhoneStatePermissionDenied
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE -> PermissionError.StoragePermissionDenied
                    Manifest.permission.POST_NOTIFICATIONS -> PermissionError.NotificationPermissionDenied
                    else -> PermissionError.StoragePermissionDenied // Default fallback
                }
                onPermissionDenied(permission, error)
            }
        }
    }

    /**
     * Validates that all critical permissions are available for recording
     * @throws PermissionError if critical permissions are missing
     */
    @Throws(PermissionError::class)
    fun validateRecordingPermissions() {
        if (!hasMicrophonePermission()) {
            throw PermissionError.MicrophonePermissionDenied
        }
        
        if (!hasPhoneStatePermission()) {
            throw PermissionError.PhoneStatePermissionDenied
        }
        
        if (!hasStoragePermission()) {
            throw PermissionError.StoragePermissionDenied
        }
    }

    /**
     * Checks if the app can start recording based on current permissions
     */
    fun canStartRecording(): Boolean {
        return try {
            validateRecordingPermissions()
            true
        } catch (e: PermissionError) {
            false
        }
    }
}
