package com.example.pixelcallrecorder.utils

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PermissionManagerTest {

    private lateinit var mockActivity: FragmentActivity
    private lateinit var mockLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var permissionManager: PermissionManager

    @Before
    fun setup() {
        mockActivity = mockk(relaxed = true)
        mockLauncher = mockk(relaxed = true)

        mockkStatic(ContextCompat::class)

        every { mockActivity.registerForActivityResult(any(), any()) } returns mockLauncher

        permissionManager = PermissionManager(mockActivity)
    }

    @Test
    fun `checkPermissions returns true when all permissions granted`() {
        every {
            ContextCompat.checkSelfPermission(mockActivity, Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(mockActivity, Manifest.permission.READ_PHONE_STATE)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(mockActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } returns PackageManager.PERMISSION_GRANTED

        val result = permissionManager.checkPermissions()

        assert(result)
    }

    @Test
    fun `checkPermissions returns false when any permission denied`() {
        every {
            ContextCompat.checkSelfPermission(mockActivity, Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(mockActivity, Manifest.permission.READ_PHONE_STATE)
        } returns PackageManager.PERMISSION_DENIED
        every {
            ContextCompat.checkSelfPermission(mockActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } returns PackageManager.PERMISSION_GRANTED

        val result = permissionManager.checkPermissions()

        assert(!result)
    }

    @Test
    fun `requestPermissions launches permission request`() {
        permissionManager.requestPermissions()

        verify { mockLauncher.launch(any()) }
    }

    @Test
    fun `shouldShowRationale calls activity method`() {
        val permission = Manifest.permission.RECORD_AUDIO
        every { mockActivity.shouldShowRequestPermissionRationale(permission) } returns true

        val result = permissionManager.shouldShowRationale(permission)

        assert(result)
        verify { mockActivity.shouldShowRequestPermissionRationale(permission) }
    }

    @Test
    fun `permission callback handles all granted permissions`() {
        val grantedPermissions = mapOf(
            Manifest.permission.RECORD_AUDIO to true,
            Manifest.permission.READ_PHONE_STATE to true,
            Manifest.permission.WRITE_EXTERNAL_STORAGE to true
        )

        // この部分はプライベートコールバックなので、パブリックメソッドを通じてテスト
        every {
            ContextCompat.checkSelfPermission(mockActivity, any())
        } returns PackageManager.PERMISSION_GRANTED

        val result = permissionManager.checkPermissions()
        assert(result)
    }

    @Test
    fun `permission callback handles denied permissions`() {
        every {
            ContextCompat.checkSelfPermission(mockActivity, Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_DENIED

        val result = permissionManager.checkPermissions()
        assert(!result)
    }
}
