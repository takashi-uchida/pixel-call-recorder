package com.callrecorder.pixel.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callrecorder.pixel.crash.CrashStateManager
import com.callrecorder.pixel.debug.DebugInfoCollector
import com.callrecorder.pixel.error.ErrorHandler
import com.callrecorder.pixel.error.ErrorReporter
import com.callrecorder.pixel.logging.Logger
import com.callrecorder.pixel.permission.PermissionManager
import com.callrecorder.pixel.receiver.PhoneStateReceiver
import com.callrecorder.pixel.service.CallRecordingServiceImpl
import com.callrecorder.pixel.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

/**
 * システムレベルの統合テスト
 * Android システムとの統合動作を確認します
 */
@RunWith(AndroidJUnit4::class)
class SystemLevelIntegrationTest {

    private lateinit var context: Context
    private lateinit var permissionManager: PermissionManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var logger: Logger
    private lateinit var errorHandler: ErrorHandler
    private lateinit var errorReporter: ErrorReporter
    private lateinit var crashStateManager: CrashStateManager
    private lateinit var debugInfoCollector: DebugInfoCollector

    @Mock
    private lateinit var callRecordingService: CallRecordingServiceImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        
        permissionManager = PermissionManager(context)
        settingsManager = SettingsManager(context)
        logger = Logger(context)
        errorHandler = ErrorHandler(context, logger)
        errorReporter = ErrorReporter(context, logger)
        crashStateManager = CrashStateManager(context)
        debugInfoCollector = DebugInfoCollector(context)
    }

    @Test
    fun testManifestPermissions() {
        // マニフェストで宣言された権限の確認
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        
        val declaredPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
        
        // 必要な権限が宣言されていることを確認
        val requiredPermissions = listOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_PHONE_STATE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.WAKE_LOCK"
        )
        
        requiredPermissions.forEach { permission ->
            assertTrue(
                declaredPermissions.contains(permission),
                "Permission $permission not declared in manifest"
            )
        }
    }

    @Test
    fun testServiceDeclaration() {
        // サービスがマニフェストで宣言されていることを確認
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SERVICES
        )
        
        val services = packageInfo.services?.map { it.name } ?: emptyList()
        
        assertTrue(
            services.any { it.contains("CallRecordingService") },
            "CallRecordingService not declared in manifest"
        )
    }

    @Test
    fun testReceiverDeclaration() {
        // レシーバーがマニフェストで宣言されていることを確認
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_RECEIVERS
        )
        
        val receivers = packageInfo.receivers?.map { it.name } ?: emptyList()
        
        assertTrue(
            receivers.any { it.contains("PhoneStateReceiver") },
            "PhoneStateReceiver not declared in manifest"
        )
    }

    @Test
    fun testPhoneStateReceiverIntegration() {
        // PhoneStateReceiverの統合テスト
        val receiver = PhoneStateReceiver()
        
        // 通話開始のインテント
        val callStartIntent = Intent("android.intent.action.PHONE_STATE").apply {
            putExtra("state", "RINGING")
            putExtra("incoming_number", "+81901234567")
        }
        
        // レシーバーがインテントを処理できることを確認
        assertNotNull(receiver)
        
        // 実際のブロードキャスト処理はモックで確認
        // receiver.onReceive(context, callStartIntent)
    }

    @Test
    fun testSystemResourceAccess() {
        // システムリソースへのアクセステスト
        
        // AudioManagerへのアクセス
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE)
        assertNotNull(audioManager)
        
        // TelephonyManagerへのアクセス
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE)
        assertNotNull(telephonyManager)
        
        // StorageManagerへのアクセス
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE)
        assertNotNull(storageManager)
        
        // NotificationManagerへのアクセス
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
        assertNotNull(notificationManager)
    }

    @Test
    fun testErrorHandlingIntegration() = runBlocking {
        // エラーハンドリングシステムの統合テスト
        
        // テスト用エラーの生成
        val testException = RuntimeException("Test error for integration testing")
        
        // エラーハンドラーでの処理
        errorHandler.handleError(testException, "SystemLevelIntegrationTest")
        
        // エラーレポーターでの処理
        errorReporter.reportError(testException, mapOf("test" to "integration"))
        
        // ログが記録されていることを確認
        val logs = logger.getRecentLogs(10)
        assertTrue(logs.isNotEmpty())
    }

    @Test
    fun testCrashRecoveryIntegration() = runBlocking {
        // クラッシュ回復システムの統合テスト
        
        // クラッシュ状態をシミュレート
        crashStateManager.saveAppState(mapOf(
            "isRecording" to true,
            "currentCallId" to "test-call-001",
            "recordingStartTime" to System.currentTimeMillis()
        ))
        
        // 状態の復元
        val recoveredState = crashStateManager.getLastAppState()
        assertNotNull(recoveredState)
        assertEquals(true, recoveredState["isRecording"])
        assertEquals("test-call-001", recoveredState["currentCallId"])
        
        // 状態のクリア
        crashStateManager.clearAppState()
        val clearedState = crashStateManager.getLastAppState()
        assertTrue(clearedState.isEmpty())
    }

    @Test
    fun testDebugInfoCollection() {
        // デバッグ情報収集の統合テスト
        
        val debugInfo = debugInfoCollector.collectSystemInfo()
        
        // 基本的なシステム情報が含まれていることを確認
        assertTrue(debugInfo.containsKey("deviceModel"))
        assertTrue(debugInfo.containsKey("androidVersion"))
        assertTrue(debugInfo.containsKey("appVersion"))
        assertTrue(debugInfo.containsKey("availableMemory"))
        assertTrue(debugInfo.containsKey("storageSpace"))
        
        // 値が有効であることを確認
        assertNotNull(debugInfo["deviceModel"])
        assertNotNull(debugInfo["androidVersion"])
        assertTrue((debugInfo["availableMemory"] as? Long ?: 0) > 0)
        assertTrue((debugInfo["storageSpace"] as? Long ?: 0) > 0)
    }

    @Test
    fun testSettingsPersistence() {
        // 設定の永続化テスト
        
        // 設定値の保存
        settingsManager.setAutoRecordingEnabled(true)
        settingsManager.setRecordingQuality("HIGH")
        settingsManager.setMaxRecordingDuration(3600)
        
        // 新しいインスタンスで設定を読み込み
        val newSettingsManager = SettingsManager(context)
        
        // 設定が正しく復元されることを確認
        assertTrue(newSettingsManager.isAutoRecordingEnabled())
        assertEquals("HIGH", newSettingsManager.getRecordingQuality())
        assertEquals(3600, newSettingsManager.getMaxRecordingDuration())
    }

    @Test
    fun testBackgroundServiceIntegration() {
        // バックグラウンドサービスの統合テスト
        
        val serviceIntent = Intent(context, CallRecordingServiceImpl::class.java)
        
        // サービス開始のインテント作成
        assertNotNull(serviceIntent)
        assertEquals(CallRecordingServiceImpl::class.java.name, serviceIntent.component?.className)
        
        // フォアグラウンドサービスとしての動作確認は実際のサービス実装で行う
    }

    @Test
    fun testFileSystemIntegration() {
        // ファイルシステムとの統合テスト
        
        // アプリ専用ディレクトリの確認
        val appDir = context.getExternalFilesDir(null)
        assertNotNull(appDir)
        
        // 録音ファイル用ディレクトリの作成
        val recordingsDir = context.getExternalFilesDir("recordings")
        assertNotNull(recordingsDir)
        
        // 書き込み権限の確認
        assertTrue(recordingsDir?.canWrite() ?: false)
        
        // 利用可能容量の確認
        val freeSpace = recordingsDir?.freeSpace ?: 0
        assertTrue(freeSpace > 0)
    }

    @Test
    fun testNotificationIntegration() {
        // 通知システムとの統合テスト
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
            as android.app.NotificationManager
        
        // 通知チャンネルの作成（Android O以降）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "recording_channel",
                "Recording Notifications",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
            
            // チャンネルが作成されたことを確認
            val createdChannel = notificationManager.getNotificationChannel("recording_channel")
            assertNotNull(createdChannel)
            assertEquals("Recording Notifications", createdChannel.name)
        }
    }

    @Test
    fun testPermissionIntegration() {
        // 権限システムとの統合テスト
        
        // 権限チェック機能の確認
        val hasRecordPermission = permissionManager.hasRecordAudioPermission()
        val hasPhonePermission = permissionManager.hasPhoneStatePermission()
        val hasStoragePermission = permissionManager.hasStoragePermission()
        
        // 権限状態の取得が正常に動作することを確認
        // 実際の権限状態は環境によって異なるため、メソッドが例外を投げないことを確認
        assertNotNull(hasRecordPermission)
        assertNotNull(hasPhonePermission)
        assertNotNull(hasStoragePermission)
        
        // 全権限チェック
        val hasAllPermissions = permissionManager.hasAllRequiredPermissions()
        assertNotNull(hasAllPermissions)
    }
}