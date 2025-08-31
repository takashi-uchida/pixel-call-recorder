package com.callrecorder.pixel.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.callrecorder.pixel.error.PermissionError
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

@RunWith(MockitoJUnitRunner::class)
class PermissionManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockActivity: Activity

    private lateinit var permissionManager: PermissionManager

    @Before
    fun setUp() {
        permissionManager = PermissionManager(mockContext)
    }

    @Test
    fun `hasMicrophonePermission returns true when permission is granted`() {
        mockStatic(ContextCompat::class.java).use { contextCompatMock ->
            contextCompatMock.`when`<Int> {
                ContextCompat.checkSelfPermission(mockContext, Manifest.permission.RECORD_AUDIO)
            }.thenReturn(PackageManager.PERMISSION_GRANTED)
            assertTrue(permissionManager.hasMicrophonePermission())
        }
    }

    @Test
    fun `hasMicrophonePermission returns false when permission is denied`() {
        mockStatic(ContextCompat::class.java).use { contextCompatMock ->
            contextCompatMock.`when`<Int> {
                ContextCompat.checkSelfPermission(mockContext, Manifest.permission.RECORD_AUDIO)
            }.thenReturn(PackageManager.PERMISSION_DENIED)
            assertFalse(permissionManager.hasMicrophonePermission())
        }
    }

    @Test
    fun `hasStoragePermission returns true for Android 10 and above`() {
        mockStatic(Build.VERSION::class.java).use { buildMock ->
            buildMock.`when`<Int> { Build.VERSION.SDK_INT }.thenReturn(29)
            assertTrue(permissionManager.hasStoragePermission())
        }
    }

    @Test
    fun `hasNotificationPermission returns true for Android below 13`() {
        mockStatic(Build.VERSION::class.java).use { buildMock ->
            buildMock.`when`<Int> { Build.VERSION.SDK_INT }.thenReturn(32)
            assertTrue(permissionManager.hasNotificationPermission())
        }
    }

    @Test
    fun `request helpers dispatch correctly`() {
        mockStatic(ActivityCompat::class.java).use { activityCompatMock ->
            permissionManager.requestMicrophonePermission(mockActivity)
            activityCompatMock.verify {
                ActivityCompat.requestPermissions(
                    mockActivity,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    PermissionManager.REQUEST_MICROPHONE_PERMISSION
                )
            }
        }
    }

    @Test
    fun `validateRecordingPermissions throws when missing`() {
        mockStatic(ContextCompat::class.java).use { cc ->
            cc.`when`<Int> { ContextCompat.checkSelfPermission(mockContext, Manifest.permission.RECORD_AUDIO) }
                .thenReturn(PackageManager.PERMISSION_DENIED)
            assertFailsWith<PermissionError.MicrophonePermissionDenied> {
                permissionManager.validateRecordingPermissions()
            }
        }
    }

    private inline fun <reified T> MockedStatic<T>.`when`(methodCall: () -> Any): MockedStatic.Verification {
        return `when`(methodCall)
    }
}

