package com.callrecorder.pixel.permission

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*

@RunWith(MockitoJUnitRunner::class)
class PermissionDialogHelperTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockAlertDialogBuilder: AlertDialog.Builder

    @Mock
    private lateinit var mockAlertDialog: AlertDialog

    private lateinit var permissionDialogHelper: PermissionDialogHelper

    @Before
    fun setUp() {
        permissionDialogHelper = PermissionDialogHelper(mockContext)

        whenever(mockAlertDialogBuilder.setTitle(any<String>())).thenReturn(mockAlertDialogBuilder)
        whenever(mockAlertDialogBuilder.setMessage(any<CharSequence>())).thenReturn(mockAlertDialogBuilder)
        whenever(mockAlertDialogBuilder.setPositiveButton(any<String>(), any())).thenReturn(mockAlertDialogBuilder)
        whenever(mockAlertDialogBuilder.setNegativeButton(any<String>(), any())).thenReturn(mockAlertDialogBuilder)
        whenever(mockAlertDialogBuilder.setCancelable(any<Boolean>())).thenReturn(mockAlertDialogBuilder)
        whenever(mockAlertDialogBuilder.show()).thenReturn(mockAlertDialog)
    }

    @Test
    fun microphone_rationale_dialog() {
        mockStatic(AlertDialog.Builder::class.java).use { builderMock ->
            builderMock.`when`<AlertDialog.Builder> { AlertDialog.Builder(mockContext) }
                .thenReturn(mockAlertDialogBuilder)

            permissionDialogHelper.showPermissionRationaleDialog(
                Manifest.permission.RECORD_AUDIO,
                onPositive = { }
            )

            verify(mockAlertDialogBuilder).setTitle(eq("マイクへのアクセス"))
            verify(mockAlertDialogBuilder).setMessage(argThat<CharSequence> {
                this.contains("通話を録音するため") && this.contains("マイク")
            })
        }
    }

    @Test
    fun notification_rationale_dialog() {
        mockStatic(AlertDialog.Builder::class.java).use { builderMock ->
            builderMock.`when`<AlertDialog.Builder> { AlertDialog.Builder(mockContext) }
                .thenReturn(mockAlertDialogBuilder)

            permissionDialogHelper.showPermissionRationaleDialog(
                Manifest.permission.POST_NOTIFICATIONS,
                onPositive = { }
            )

            verify(mockAlertDialogBuilder).setTitle(eq("通知の許可"))
            verify(mockAlertDialogBuilder).setMessage(argThat<CharSequence> {
                this.contains("録音状態をお知らせ") && this.contains("通知")
            })
        }
    }

    private inline fun <reified T> MockedStatic<T>.`when`(methodCall: () -> Any): MockedStatic.Verification {
        return `when`(methodCall)
    }
}

