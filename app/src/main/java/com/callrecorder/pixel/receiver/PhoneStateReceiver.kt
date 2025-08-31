package com.callrecorder.pixel.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.callrecorder.pixel.data.model.CallInfo
import com.callrecorder.pixel.service.CallRecordingServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Broadcast receiver for detecting phone state changes and managing automatic recording.
 * Listens for incoming and outgoing calls and triggers recording based on user preferences.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneStateReceiver"
        
        // Call state tracking
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var currentPhoneNumber: String? = null
        private var isIncomingCall = false
        private var callStartTime: LocalDateTime? = null
    }

    private val receiverScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent")
            return
        }

        try {
            when (intent.action) {
                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                    handlePhoneStateChange(context, intent)
                }
                Intent.ACTION_NEW_OUTGOING_CALL -> {
                    handleOutgoingCall(context, intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling phone state change", e)
        }
    }

    /**
     * Handles phone state changes (IDLE, RINGING, OFFHOOK)
     */
    private fun handlePhoneStateChange(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "Phone state changed: $state, number: $phoneNumber")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                handleRingingState(context, phoneNumber)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                handleOffHookState(context)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                handleIdleState(context)
            }
        }

        lastState = when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            else -> TelephonyManager.CALL_STATE_IDLE
        }
    }

    /**
     * Handles outgoing call detection
     */
    private fun handleOutgoingCall(context: Context, intent: Intent) {
        val phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
        Log.d(TAG, "Outgoing call detected: $phoneNumber")
        
        currentPhoneNumber = phoneNumber
        isIncomingCall = false
        
        // Note: Actual recording will start when call goes OFFHOOK
    }

    /**
     * Handles RINGING state (incoming call)
     */
    private fun handleRingingState(context: Context, phoneNumber: String?) {
        Log.d(TAG, "Call ringing: $phoneNumber")
        
        if (lastState == TelephonyManager.CALL_STATE_IDLE) {
            // New incoming call
            currentPhoneNumber = phoneNumber
            isIncomingCall = true
            Log.i(TAG, "Incoming call detected from: $phoneNumber")
        }
    }

    /**
     * Handles OFFHOOK state (call answered/active)
     */
    private fun handleOffHookState(context: Context) {
        Log.d(TAG, "Call off hook")
        
        when (lastState) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // Incoming call answered
                Log.i(TAG, "Incoming call answered")
                startRecordingIfEnabled(context)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Outgoing call connected
                Log.i(TAG, "Outgoing call connected")
                startRecordingIfEnabled(context)
            }
        }
        
        callStartTime = LocalDateTime.now()
    }

    /**
     * Handles IDLE state (call ended)
     */
    private fun handleIdleState(context: Context) {
        Log.d(TAG, "Call ended")
        
        if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
            // Call was active, now ended
            Log.i(TAG, "Active call ended, stopping recording")
            stopRecordingIfActive(context)
        }
        
        // Reset call tracking variables
        resetCallState()
    }

    /**
     * Starts recording if automatic recording is enabled and conditions are met
     */
    private fun startRecordingIfEnabled(context: Context) {
        receiverScope.launch {
            try {
                // Check if automatic recording is enabled (TODO: implement settings check)
                if (!isAutomaticRecordingEnabled(context)) {
                    Log.d(TAG, "Automatic recording is disabled")
                    return@launch
                }

                // Check if we have necessary permissions (TODO: implement permission check)
                if (!hasRequiredPermissions(context)) {
                    Log.w(TAG, "Missing required permissions for recording")
                    return@launch
                }

                val phoneNumber = currentPhoneNumber
                if (phoneNumber.isNullOrBlank()) {
                    Log.w(TAG, "No phone number available for recording")
                    return@launch
                }

                // Create CallInfo object
                val callInfo = CallInfo(
                    callId = generateCallId(),
                    phoneNumber = phoneNumber,
                    contactName = getContactName(context, phoneNumber),
                    isIncoming = isIncomingCall,
                    startTime = callStartTime ?: LocalDateTime.now()
                )

                // Start the recording service
                val serviceIntent = Intent(context, CallRecordingServiceImpl::class.java).apply {
                    action = CallRecordingServiceImpl.ACTION_START_RECORDING
                    putExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO, callInfo)
                }

                context.startForegroundService(serviceIntent)
                Log.i(TAG, "Started recording service for call: $phoneNumber")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting automatic recording", e)
            }
        }
    }

    /**
     * Stops recording if currently active
     */
    private fun stopRecordingIfActive(context: Context) {
        receiverScope.launch {
            try {
                val serviceIntent = Intent(context, CallRecordingServiceImpl::class.java).apply {
                    action = CallRecordingServiceImpl.ACTION_STOP_RECORDING
                }

                context.startService(serviceIntent)
                Log.i(TAG, "Sent stop recording command")

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
            }
        }
    }

    /**
     * Resets call state tracking variables
     */
    private fun resetCallState() {
        currentPhoneNumber = null
        isIncomingCall = false
        callStartTime = null
    }

    /**
     * Checks if automatic recording is enabled in settings
     * TODO: Implement actual settings check
     */
    private fun isAutomaticRecordingEnabled(context: Context): Boolean {
        // For now, return true. This should check user preferences
        return true
    }

    /**
     * Checks if all required permissions are granted
     * TODO: Implement actual permission check
     */
    private fun hasRequiredPermissions(context: Context): Boolean {
        // For now, return true. This should check actual permissions
        return true
    }

    /**
     * Gets contact name for a phone number
     * TODO: Implement contact lookup
     */
    private fun getContactName(context: Context, phoneNumber: String): String? {
        // For now, return null. This should lookup contact name from contacts database
        return null
    }

    /**
     * Generates a unique call ID
     */
    private fun generateCallId(): String {
        return "call_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }
}