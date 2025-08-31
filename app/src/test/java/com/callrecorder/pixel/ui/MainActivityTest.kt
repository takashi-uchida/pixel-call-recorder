package com.callrecorder.pixel.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.callrecorder.pixel.permission.PermissionManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for MainActivity
 * Tests the basic functionality of the main UI components
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainActivityTest {

    private lateinit var activity: MainActivity
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        activity = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .resume()
            .get()
    }

    @Test
    fun `activity should be created successfully`() {
        // This test verifies that the activity can be created without crashing
        // which means all the UI components and dependencies are properly initialized
        assert(activity != null)
    }

    @Test
    fun `permission manager should be initialized`() {
        // This test verifies that the PermissionManager can be instantiated
        val permissionManager = PermissionManager(context)
        assert(permissionManager != null)
    }
}