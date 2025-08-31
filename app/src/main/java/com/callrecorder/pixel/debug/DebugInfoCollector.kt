package com.callrecorder.pixel.debug

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import com.callrecorder.pixel.logging.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Collects comprehensive debug information for troubleshooting and support.
 * Gathers system info, app state, device capabilities, and performance metrics.
 */
class DebugInfoCollector(private val context: Context) {

    private val logger = Logger.getInstance(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    companion object {
        private const val TAG = "DebugInfoCollector"
    }

    /**
     * Collects comprehensive debug information
     */
    fun collectDebugInfo(): DebugInfo {
        logger.debug(TAG, "Collecting debug information")
        
        return DebugInfo(
            timestamp = System.currentTimeMillis(),
            deviceInfo = collectDeviceInfo(),
            systemInfo = collectSystemInfo(),
            appInfo = collectAppInfo(),
            storageInfo = collectStorageInfo(),
            memoryInfo = collectMemoryInfo(),
            permissionsInfo = collectPermissionsInfo(),
            audioInfo = collectAudioInfo(),
            telephonyInfo = collectTelephonyInfo(),
            environmentInfo = collectEnvironmentInfo()
        )
    }

    /**
     * Collects device information
     */
    private fun collectDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            brand = Build.BRAND,
            hardware = Build.HARDWARE,
            board = Build.BOARD,
            fingerprint = Build.FINGERPRINT,
            bootloader = Build.BOOTLOADER,
            radioVersion = Build.getRadioVersion(),
            serialNumber = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Build.getSerial()
                } else {
                    @Suppress("DEPRECATION")
                    Build.SERIAL
                }
            } catch (e: SecurityException) {
                "Permission denied"
            }
        )
    }

    /**
     * Collects system information
     */
    private fun collectSystemInfo(): SystemInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
        return SystemInfo(
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            buildId = Build.ID,
            buildTime = Build.TIME,
            buildType = Build.TYPE,
            buildUser = Build.USER,
            buildHost = Build.HOST,
            kernelVersion = System.getProperty("os.version") ?: "Unknown",
            javaVmVersion = System.getProperty("java.vm.version") ?: "Unknown",
            javaVmName = System.getProperty("java.vm.name") ?: "Unknown",
            timezone = TimeZone.getDefault().id,
            locale = Locale.getDefault().toString(),
            isLowRamDevice = activityManager.isLowRamDevice,
            largeMemoryClass = activityManager.largeMemoryClass,
            memoryClass = activityManager.memoryClass
        )
    }

    /**
     * Collects application information
     */
    private fun collectAppInfo(): AppInfo {
        val packageManager = context.packageManager
        val packageInfo = try {
            packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        val applicationInfo = try {
            packageManager.getApplicationInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        return AppInfo(
            packageName = context.packageName,
            versionName = packageInfo?.versionName ?: "Unknown",
            versionCode = packageInfo?.longVersionCode ?: 0L,
            targetSdkVersion = applicationInfo?.targetSdkVersion ?: 0,
            minSdkVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                applicationInfo?.minSdkVersion ?: 0
            } else {
                0
            },
            installTime = packageInfo?.firstInstallTime ?: 0L,
            updateTime = packageInfo?.lastUpdateTime ?: 0L,
            dataDir = applicationInfo?.dataDir ?: "Unknown",
            sourceDir = applicationInfo?.sourceDir ?: "Unknown",
            isDebuggable = applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            isSystemApp = applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        )
    }

    /**
     * Collects storage information
     */
    private fun collectStorageInfo(): StorageInfo {
        val internalStorage = getStorageStats(Environment.getDataDirectory())
        val externalStorage = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            getStorageStats(Environment.getExternalStorageDirectory())
        } else {
            StorageStats(0L, 0L, 0L)
        }

        val appDataDir = context.filesDir
        val appStorage = getStorageStats(appDataDir)

        return StorageInfo(
            internalStorage = internalStorage,
            externalStorage = externalStorage,
            appStorage = appStorage,
            externalStorageState = Environment.getExternalStorageState(),
            isExternalStorageEmulated = Environment.isExternalStorageEmulated(),
            isExternalStorageRemovable = Environment.isExternalStorageRemovable()
        )
    }

    /**
     * Gets storage statistics for a directory
     */
    private fun getStorageStats(directory: File): StorageStats {
        return try {
            val stat = StatFs(directory.absolutePath)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes

            StorageStats(totalBytes, availableBytes, usedBytes)
        } catch (e: Exception) {
            logger.warn(TAG, "Failed to get storage stats for ${directory.absolutePath}", e)
            StorageStats(0L, 0L, 0L)
        }
    }

    /**
     * Collects memory information
     */
    private fun collectMemoryInfo(): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val runtime = Runtime.getRuntime()

        return MemoryInfo(
            totalRam = memoryInfo.totalMem,
            availableRam = memoryInfo.availMem,
            usedRam = memoryInfo.totalMem - memoryInfo.availMem,
            threshold = memoryInfo.threshold,
            isLowMemory = memoryInfo.lowMemory,
            jvmMaxMemory = runtime.maxMemory(),
            jvmTotalMemory = runtime.totalMemory(),
            jvmFreeMemory = runtime.freeMemory(),
            jvmUsedMemory = runtime.totalMemory() - runtime.freeMemory()
        )
    }

    /**
     * Collects permissions information
     */
    private fun collectPermissionsInfo(): PermissionsInfo {
        val packageManager = context.packageManager
        val permissions = mutableMapOf<String, Boolean>()

        val criticalPermissions = listOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.FOREGROUND_SERVICE,
            android.Manifest.permission.WAKE_LOCK
        )

        criticalPermissions.forEach { permission ->
            permissions[permission] = packageManager.checkPermission(
                permission, 
                context.packageName
            ) == PackageManager.PERMISSION_GRANTED
        }

        return PermissionsInfo(
            grantedPermissions = permissions.filterValues { it }.keys.toList(),
            deniedPermissions = permissions.filterValues { !it }.keys.toList(),
            allPermissions = permissions
        )
    }

    /**
     * Collects audio system information
     */
    private fun collectAudioInfo(): AudioInfo {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

        return AudioInfo(
            audioMode = audioManager.mode,
            isMicrophoneMute = audioManager.isMicrophoneMute,
            isSpeakerphoneOn = audioManager.isSpeakerphoneOn,
            isBluetoothScoOn = audioManager.isBluetoothScoOn,
            isWiredHeadsetOn = audioManager.isWiredHeadsetOn,
            streamVolumeMusic = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC),
            streamVolumeCall = audioManager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL),
            streamMaxVolumeMusic = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC),
            streamMaxVolumeCall = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL)
        )
    }

    /**
     * Collects telephony information
     */
    private fun collectTelephonyInfo(): TelephonyInfo {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        return TelephonyInfo(
            networkOperatorName = telephonyManager.networkOperatorName ?: "Unknown",
            networkOperator = telephonyManager.networkOperator ?: "Unknown",
            simOperatorName = telephonyManager.simOperatorName ?: "Unknown",
            simOperator = telephonyManager.simOperator ?: "Unknown",
            phoneType = telephonyManager.phoneType,
            networkType = telephonyManager.networkType,
            hasIccCard = telephonyManager.hasIccCard(),
            isNetworkRoaming = telephonyManager.isNetworkRoaming,
            callState = telephonyManager.callState,
            dataState = telephonyManager.dataState
        )
    }

    /**
     * Collects environment information
     */
    private fun collectEnvironmentInfo(): EnvironmentInfo {
        return EnvironmentInfo(
            currentTimeMillis = System.currentTimeMillis(),
            currentTimeFormatted = dateFormat.format(Date()),
            uptime = android.os.SystemClock.uptimeMillis(),
            elapsedRealtime = android.os.SystemClock.elapsedRealtime(),
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            systemProperties = collectSystemProperties()
        )
    }

    /**
     * Collects relevant system properties
     */
    private fun collectSystemProperties(): Map<String, String> {
        val properties = mutableMapOf<String, String>()
        
        val relevantProperties = listOf(
            "ro.build.version.release",
            "ro.build.version.sdk",
            "ro.product.manufacturer",
            "ro.product.model",
            "ro.product.device",
            "ro.hardware",
            "ro.board.platform",
            "dalvik.vm.heapsize",
            "dalvik.vm.heapgrowthlimit",
            "ro.telephony.call_ring.multiple"
        )

        relevantProperties.forEach { property ->
            try {
                val value = System.getProperty(property)
                if (value != null) {
                    properties[property] = value
                }
            } catch (e: Exception) {
                // Ignore properties we can't access
            }
        }

        return properties
    }

    /**
     * Exports debug information to a formatted string
     */
    fun exportDebugInfo(): String {
        val debugInfo = collectDebugInfo()
        
        return buildString {
            appendLine("=== CALL RECORDER DEBUG INFORMATION ===")
            appendLine("Generated: ${dateFormat.format(Date(debugInfo.timestamp))}")
            appendLine()

            appendLine("=== DEVICE INFO ===")
            with(debugInfo.deviceInfo) {
                appendLine("Manufacturer: $manufacturer")
                appendLine("Model: $model")
                appendLine("Device: $device")
                appendLine("Product: $product")
                appendLine("Brand: $brand")
                appendLine("Hardware: $hardware")
                appendLine("Board: $board")
                appendLine("Bootloader: $bootloader")
                appendLine("Radio Version: $radioVersion")
                appendLine("Serial: $serialNumber")
                appendLine("Fingerprint: $fingerprint")
            }
            appendLine()

            appendLine("=== SYSTEM INFO ===")
            with(debugInfo.systemInfo) {
                appendLine("Android Version: $androidVersion (API $apiLevel)")
                appendLine("Security Patch: $securityPatch")
                appendLine("Build ID: $buildId")
                appendLine("Build Type: $buildType")
                appendLine("Build Time: ${Date(buildTime)}")
                appendLine("Kernel Version: $kernelVersion")
                appendLine("JVM: $javaVmName $javaVmVersion")
                appendLine("Timezone: $timezone")
                appendLine("Locale: $locale")
                appendLine("Low RAM Device: $isLowRamDevice")
                appendLine("Memory Class: $memoryClass MB")
                appendLine("Large Memory Class: $largeMemoryClass MB")
            }
            appendLine()

            appendLine("=== APP INFO ===")
            with(debugInfo.appInfo) {
                appendLine("Package: $packageName")
                appendLine("Version: $versionName ($versionCode)")
                appendLine("Target SDK: $targetSdkVersion")
                appendLine("Min SDK: $minSdkVersion")
                appendLine("Install Time: ${Date(installTime)}")
                appendLine("Update Time: ${Date(updateTime)}")
                appendLine("Data Dir: $dataDir")
                appendLine("Source Dir: $sourceDir")
                appendLine("Debuggable: $isDebuggable")
                appendLine("System App: $isSystemApp")
            }
            appendLine()

            appendLine("=== STORAGE INFO ===")
            with(debugInfo.storageInfo) {
                appendLine("Internal Storage:")
                appendLine("  Total: ${formatBytes(internalStorage.totalBytes)}")
                appendLine("  Available: ${formatBytes(internalStorage.availableBytes)}")
                appendLine("  Used: ${formatBytes(internalStorage.usedBytes)}")
                appendLine("External Storage:")
                appendLine("  State: $externalStorageState")
                appendLine("  Total: ${formatBytes(externalStorage.totalBytes)}")
                appendLine("  Available: ${formatBytes(externalStorage.availableBytes)}")
                appendLine("  Used: ${formatBytes(externalStorage.usedBytes)}")
                appendLine("  Emulated: $isExternalStorageEmulated")
                appendLine("  Removable: $isExternalStorageRemovable")
                appendLine("App Storage:")
                appendLine("  Total: ${formatBytes(appStorage.totalBytes)}")
                appendLine("  Available: ${formatBytes(appStorage.availableBytes)}")
                appendLine("  Used: ${formatBytes(appStorage.usedBytes)}")
            }
            appendLine()

            appendLine("=== MEMORY INFO ===")
            with(debugInfo.memoryInfo) {
                appendLine("System RAM:")
                appendLine("  Total: ${formatBytes(totalRam)}")
                appendLine("  Available: ${formatBytes(availableRam)}")
                appendLine("  Used: ${formatBytes(usedRam)}")
                appendLine("  Threshold: ${formatBytes(threshold)}")
                appendLine("  Low Memory: $isLowMemory")
                appendLine("JVM Memory:")
                appendLine("  Max: ${formatBytes(jvmMaxMemory)}")
                appendLine("  Total: ${formatBytes(jvmTotalMemory)}")
                appendLine("  Free: ${formatBytes(jvmFreeMemory)}")
                appendLine("  Used: ${formatBytes(jvmUsedMemory)}")
            }
            appendLine()

            appendLine("=== PERMISSIONS ===")
            with(debugInfo.permissionsInfo) {
                appendLine("Granted Permissions:")
                grantedPermissions.forEach { appendLine("  ✓ $it") }
                appendLine("Denied Permissions:")
                deniedPermissions.forEach { appendLine("  ✗ $it") }
            }
            appendLine()

            appendLine("=== AUDIO INFO ===")
            with(debugInfo.audioInfo) {
                appendLine("Audio Mode: $audioMode")
                appendLine("Microphone Mute: $isMicrophoneMute")
                appendLine("Speakerphone: $isSpeakerphoneOn")
                appendLine("Bluetooth SCO: $isBluetoothScoOn")
                appendLine("Wired Headset: $isWiredHeadsetOn")
                appendLine("Music Volume: $streamVolumeMusic/$streamMaxVolumeMusic")
                appendLine("Call Volume: $streamVolumeCall/$streamMaxVolumeCall")
            }
            appendLine()

            appendLine("=== TELEPHONY INFO ===")
            with(debugInfo.telephonyInfo) {
                appendLine("Network Operator: $networkOperatorName ($networkOperator)")
                appendLine("SIM Operator: $simOperatorName ($simOperator)")
                appendLine("Phone Type: $phoneType")
                appendLine("Network Type: $networkType")
                appendLine("Has SIM Card: $hasIccCard")
                appendLine("Roaming: $isNetworkRoaming")
                appendLine("Call State: $callState")
                appendLine("Data State: $dataState")
            }
            appendLine()

            appendLine("=== ENVIRONMENT ===")
            with(debugInfo.environmentInfo) {
                appendLine("Current Time: $currentTimeFormatted")
                appendLine("Uptime: ${uptime / 1000} seconds")
                appendLine("Elapsed Realtime: ${elapsedRealtime / 1000} seconds")
                appendLine("Available Processors: $availableProcessors")
                appendLine("System Properties:")
                systemProperties.forEach { (key, value) ->
                    appendLine("  $key = $value")
                }
            }
        }
    }

    /**
     * Formats bytes to human readable format
     */
    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.1f GB".format(gb)
    }
}

/**
 * Data classes for debug information
 */
data class DebugInfo(
    val timestamp: Long,
    val deviceInfo: DeviceInfo,
    val systemInfo: SystemInfo,
    val appInfo: AppInfo,
    val storageInfo: StorageInfo,
    val memoryInfo: MemoryInfo,
    val permissionsInfo: PermissionsInfo,
    val audioInfo: AudioInfo,
    val telephonyInfo: TelephonyInfo,
    val environmentInfo: EnvironmentInfo
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val brand: String,
    val hardware: String,
    val board: String,
    val fingerprint: String,
    val bootloader: String,
    val radioVersion: String,
    val serialNumber: String
)

data class SystemInfo(
    val androidVersion: String,
    val apiLevel: Int,
    val securityPatch: String,
    val buildId: String,
    val buildTime: Long,
    val buildType: String,
    val buildUser: String,
    val buildHost: String,
    val kernelVersion: String,
    val javaVmVersion: String,
    val javaVmName: String,
    val timezone: String,
    val locale: String,
    val isLowRamDevice: Boolean,
    val largeMemoryClass: Int,
    val memoryClass: Int
)

data class AppInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val targetSdkVersion: Int,
    val minSdkVersion: Int,
    val installTime: Long,
    val updateTime: Long,
    val dataDir: String,
    val sourceDir: String,
    val isDebuggable: Boolean,
    val isSystemApp: Boolean
)

data class StorageInfo(
    val internalStorage: StorageStats,
    val externalStorage: StorageStats,
    val appStorage: StorageStats,
    val externalStorageState: String,
    val isExternalStorageEmulated: Boolean,
    val isExternalStorageRemovable: Boolean
)

data class StorageStats(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long
)

data class MemoryInfo(
    val totalRam: Long,
    val availableRam: Long,
    val usedRam: Long,
    val threshold: Long,
    val isLowMemory: Boolean,
    val jvmMaxMemory: Long,
    val jvmTotalMemory: Long,
    val jvmFreeMemory: Long,
    val jvmUsedMemory: Long
)

data class PermissionsInfo(
    val grantedPermissions: List<String>,
    val deniedPermissions: List<String>,
    val allPermissions: Map<String, Boolean>
)

data class AudioInfo(
    val audioMode: Int,
    val isMicrophoneMute: Boolean,
    val isSpeakerphoneOn: Boolean,
    val isBluetoothScoOn: Boolean,
    val isWiredHeadsetOn: Boolean,
    val streamVolumeMusic: Int,
    val streamVolumeCall: Int,
    val streamMaxVolumeMusic: Int,
    val streamMaxVolumeCall: Int
)

data class TelephonyInfo(
    val networkOperatorName: String,
    val networkOperator: String,
    val simOperatorName: String,
    val simOperator: String,
    val phoneType: Int,
    val networkType: Int,
    val hasIccCard: Boolean,
    val isNetworkRoaming: Boolean,
    val callState: Int,
    val dataState: Int
)

data class EnvironmentInfo(
    val currentTimeMillis: Long,
    val currentTimeFormatted: String,
    val uptime: Long,
    val elapsedRealtime: Long,
    val availableProcessors: Int,
    val systemProperties: Map<String, String>
)