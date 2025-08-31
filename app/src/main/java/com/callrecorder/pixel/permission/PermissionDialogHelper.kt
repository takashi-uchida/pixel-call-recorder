package com.callrecorder.pixel.permission

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface

/**
 * Helper class for creating permission-related dialogs
 */
class PermissionDialogHelper(private val context: Context) {

    /**
     * Shows a rationale dialog explaining why a permission is needed
     */
    fun showPermissionRationaleDialog(
        permission: String,
        onPositive: () -> Unit,
        onNegative: () -> Unit = {}
    ) {
        val permissionManager = PermissionManager(context)
        val explanation = permissionManager.getPermissionExplanation(permission)
        val title = getPermissionTitle(permission)

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(explanation)
            .setPositiveButton("許可する") { _, _ -> onPositive() }
            .setNegativeButton("キャンセル") { _, _ -> onNegative() }
            .setCancelable(false)
            .show()
    }

    /**
     * Shows a dialog when permission is permanently denied
     */
    fun showPermissionDeniedDialog(
        permission: String,
        onOpenSettings: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val title = getPermissionTitle(permission)
        val message = "${getPermissionTitle(permission)}が拒否されました。\n\n" +
                "アプリを正常に動作させるためには、設定画面から手動で権限を許可する必要があります。\n\n" +
                "設定画面を開きますか？"

        AlertDialog.Builder(context)
            .setTitle("権限が必要です")
            .setMessage(message)
            .setPositiveButton("設定を開く") { _, _ -> onOpenSettings() }
            .setNegativeButton("キャンセル") { _, _ -> onCancel() }
            .setCancelable(false)
            .show()
    }

    /**
     * Shows a dialog explaining all required permissions
     */
    fun showAllPermissionsExplanationDialog(
        onRequestPermissions: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val message = """
            通話録音アプリを使用するために、以下の権限が必要です：
            
            📱 電話状態へのアクセス
            通話の開始と終了を検出し、自動録音を行います
            
            🎤 マイクへのアクセス
            高品質な音声録音を行います
            
            💾 ストレージへのアクセス
            録音ファイルを安全に保存します
            
            🔔 通知の許可
            録音状態をお知らせします
            
            これらの権限は録音機能に必要不可欠です。
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle("権限の許可が必要です")
            .setMessage(message)
            .setPositiveButton("権限を許可") { _, _ -> onRequestPermissions() }
            .setNegativeButton("後で") { _, _ -> onCancel() }
            .setCancelable(false)
            .show()
    }

    /**
     * Shows a dialog when some permissions are missing
     */
    fun showMissingPermissionsDialog(
        missingPermissions: List<String>,
        onRequestPermissions: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val permissionNames = missingPermissions.map { getPermissionTitle(it) }
        val message = "以下の権限が不足しています：\n\n${permissionNames.joinToString("\n• ", "• ")}\n\n" +
                "アプリを正常に動作させるために、これらの権限を許可してください。"

        AlertDialog.Builder(context)
            .setTitle("権限が不足しています")
            .setMessage(message)
            .setPositiveButton("権限を許可") { _, _ -> onRequestPermissions() }
            .setNegativeButton("キャンセル") { _, _ -> onCancel() }
            .setCancelable(false)
            .show()
    }

    /**
     * Shows a confirmation dialog before opening app settings
     */
    fun showOpenSettingsConfirmationDialog(
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val message = """
            権限を手動で許可するために設定画面を開きます。
            
            設定画面で以下の手順を行ってください：
            1. 「権限」または「アプリの権限」をタップ
            2. 必要な権限をすべて「許可」に変更
            3. アプリに戻る
            
            設定画面を開きますか？
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle("設定画面を開く")
            .setMessage(message)
            .setPositiveButton("開く") { _, _ -> onConfirm() }
            .setNegativeButton("キャンセル") { _, _ -> onCancel() }
            .setCancelable(true)
            .show()
    }

    /**
     * Shows a general permission explanation dialog
     */
    fun showPermissionExplanationDialog(
        onPositive: () -> Unit,
        onNegative: () -> Unit = {}
    ) {
        val message = """
            通話録音アプリを使用するために、以下の権限が必要です：
            
            📱 電話状態へのアクセス
            通話の開始と終了を検出し、自動録音を行います
            
            🎤 マイクへのアクセス
            高品質な音声録音を行います
            
            💾 ストレージへのアクセス
            録音ファイルを安全に保存します
            
            これらの権限は録音機能に必要不可欠です。
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle("権限の許可が必要です")
            .setMessage(message)
            .setPositiveButton("権限を許可") { _, _ -> onPositive() }
            .setNegativeButton("キャンセル") { _, _ -> onNegative() }
            .setCancelable(false)
            .show()
    }

    /**
     * Shows a general information dialog
     */
    fun showInfoDialog(
        title: String,
        message: String,
        onDismiss: () -> Unit = {}
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> onDismiss() }
            .setCancelable(true)
            .show()
    }

    /**
     * Gets user-friendly title for a permission
     */
    private fun getPermissionTitle(permission: String): String {
        return when (permission) {
            android.Manifest.permission.RECORD_AUDIO -> "マイクへのアクセス"
            android.Manifest.permission.READ_PHONE_STATE -> "電話状態へのアクセス"
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE -> "ストレージへのアクセス"
            android.Manifest.permission.POST_NOTIFICATIONS -> "通知の許可"
            else -> "権限"
        }
    }
}