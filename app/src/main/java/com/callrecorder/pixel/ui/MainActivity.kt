package com.callrecorder.pixel.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.callrecorder.pixel.R
import com.callrecorder.pixel.permission.PermissionManager
import com.callrecorder.pixel.permission.PermissionDialogHelper
import com.callrecorder.pixel.service.CallRecordingService
import com.google.android.material.button.MaterialButton

/**
 * Main activity for the call recording app
 * Provides UI for recording control, permission management, and navigation
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var tvRecordingStatus: TextView
    private lateinit var btnRecordingControl: MaterialButton
    private lateinit var btnGrantPermissions: MaterialButton
    private lateinit var btnViewRecordings: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var recordingIndicator: View
    private lateinit var ivMicPermissionStatus: ImageView
    private lateinit var ivPhonePermissionStatus: ImageView
    private lateinit var ivStoragePermissionStatus: ImageView
    
    private lateinit var permissionManager: PermissionManager
    private lateinit var permissionDialogHelper: PermissionDialogHelper
    private var callRecordingService: CallRecordingService? = null
    
    private var isRecording = false
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        initializeServices()
        setupClickListeners()
        updateUI()
    }
    
    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateRecordingStatus()
    }
    
    private fun initializeViews() {
        tvRecordingStatus = findViewById(R.id.tvRecordingStatus)
        btnRecordingControl = findViewById(R.id.btnRecordingControl)
        btnGrantPermissions = findViewById(R.id.btnGrantPermissions)
        btnViewRecordings = findViewById(R.id.btnViewRecordings)
        btnSettings = findViewById(R.id.btnSettings)
        recordingIndicator = findViewById(R.id.recordingIndicator)
        ivMicPermissionStatus = findViewById(R.id.ivMicPermissionStatus)
        ivPhonePermissionStatus = findViewById(R.id.ivPhonePermissionStatus)
        ivStoragePermissionStatus = findViewById(R.id.ivStoragePermissionStatus)
    }
    
    private fun initializeServices() {
        permissionManager = PermissionManager(this)
        permissionDialogHelper = PermissionDialogHelper(this)
        // CallRecordingService will be initialized when needed
    }
    
    private fun setupClickListeners() {
        btnRecordingControl.setOnClickListener {
            handleRecordingControlClick()
        }
        
        btnGrantPermissions.setOnClickListener {
            requestPermissions()
        }
        
        btnViewRecordings.setOnClickListener {
            startActivity(Intent(this, RecordingListActivity::class.java))
        }
        
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    
    private fun updateUI() {
        updatePermissionStatus()
        updateRecordingStatus()
        updateRecordingControlButton()
    }
    
    private fun updatePermissionStatus() {
        val micPermissionGranted = permissionManager.hasMicrophonePermission()
        val phonePermissionGranted = permissionManager.hasPhoneStatePermission()
        val storagePermissionGranted = permissionManager.hasStoragePermission()
        
        updatePermissionIcon(ivMicPermissionStatus, micPermissionGranted)
        updatePermissionIcon(ivPhonePermissionStatus, phonePermissionGranted)
        updatePermissionIcon(ivStoragePermissionStatus, storagePermissionGranted)
        
        val allPermissionsGranted = micPermissionGranted && phonePermissionGranted && storagePermissionGranted
        btnGrantPermissions.visibility = if (allPermissionsGranted) View.GONE else View.VISIBLE
    }
    
    private fun updatePermissionIcon(imageView: ImageView, granted: Boolean) {
        val iconRes = if (granted) R.drawable.ic_permission_granted else R.drawable.ic_permission_denied
        imageView.setImageResource(iconRes)
    }
    
    private fun updateRecordingStatus() {
        // This will be connected to the actual service in later implementation
        // For now, use the local state
        if (isRecording) {
            tvRecordingStatus.text = getString(R.string.recording_in_progress)
            tvRecordingStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            showRecordingIndicator()
        } else {
            tvRecordingStatus.text = getString(R.string.not_recording)
            tvRecordingStatus.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            hideRecordingIndicator()
        }
    }
    
    private fun updateRecordingControlButton() {
        val allPermissionsGranted = permissionManager.hasAllRequiredPermissions()
        
        if (!allPermissionsGranted) {
            btnRecordingControl.isEnabled = false
            btnRecordingControl.text = getString(R.string.permission_required)
        } else {
            btnRecordingControl.isEnabled = true
            if (isRecording) {
                btnRecordingControl.text = getString(R.string.stop_recording)
                btnRecordingControl.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            } else {
                btnRecordingControl.text = getString(R.string.start_recording)
                btnRecordingControl.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
        }
    }
    
    private fun showRecordingIndicator() {
        recordingIndicator.visibility = View.VISIBLE
        recordingIndicator.setBackgroundResource(R.drawable.recording_indicator_animated)
        val animationDrawable = recordingIndicator.background as AnimationDrawable
        animationDrawable.start()
    }
    
    private fun hideRecordingIndicator() {
        recordingIndicator.visibility = View.GONE
        val animationDrawable = recordingIndicator.background as? AnimationDrawable
        animationDrawable?.stop()
    }
    
    private fun handleRecordingControlClick() {
        if (!permissionManager.hasAllRequiredPermissions()) {
            requestPermissions()
            return
        }
        
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    private fun startRecording() {
        // This is a placeholder implementation
        // The actual service integration will be done in later tasks
        isRecording = true
        updateUI()
        
        // Show feedback to user
        permissionDialogHelper.showInfoDialog(
            "録音開始",
            "録音が開始されました。実際のサービス連携は後のタスクで実装されます。"
        )
    }
    
    private fun stopRecording() {
        // This is a placeholder implementation
        // The actual service integration will be done in later tasks
        isRecording = false
        updateUI()
        
        // Show feedback to user
        permissionDialogHelper.showInfoDialog(
            "録音停止",
            "録音が停止されました。実際のサービス連携は後のタスクで実装されます。"
        )
    }
    
    private fun requestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (missingPermissions.isNotEmpty()) {
            // Show explanation dialog first
            permissionDialogHelper.showPermissionExplanationDialog(
                onPositive = {
                    ActivityCompat.requestPermissions(this, missingPermissions, PERMISSION_REQUEST_CODE)
                },
                onNegative = {
                    permissionDialogHelper.showInfoDialog(
                        getString(R.string.permission_denied),
                        "アプリを使用するには権限が必要です。設定から手動で権限を許可してください。"
                    )
                }
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                permissionDialogHelper.showInfoDialog(
                    "権限許可完了",
                    "すべての権限が許可されました。録音機能を使用できます。"
                )
            } else {
                val deniedPermissions = permissions.filterIndexed { index, _ ->
                    grantResults[index] != PackageManager.PERMISSION_GRANTED
                }
                
                permissionDialogHelper.showInfoDialog(
                    getString(R.string.permission_denied),
                    "一部の権限が拒否されました: ${deniedPermissions.joinToString(", ")}"
                )
            }
            
            updateUI()
        }
    }
}