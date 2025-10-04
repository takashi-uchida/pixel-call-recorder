package com.callrecorder.pixel.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

/**
 * Data class representing information about a phone call.
 * Used to track call details during recording sessions.
 */
@Parcelize
data class CallInfo(
    val callId: String,
    val phoneNumber: String,
    val contactName: String?,
    val isIncoming: Boolean,
    val startTime: LocalDateTime
) : Parcelable {

    /**
     * Validates the call information
     */
    fun isValid(): Boolean {
        return callId.isNotBlank() && 
               phoneNumber.isNotBlank() && 
               phoneNumber.matches(Regex("^[+]?[0-9\\-\\s()]+$"))
    }

    /**
     * Gets display name for the call (contact name or phone number)
     */
    fun getDisplayName(): String {
        return contactName?.takeIf { it.isNotBlank() } ?: phoneNumber
    }

    /**
     * Gets formatted phone number for display
     */
    fun getFormattedPhoneNumber(): String {
        return phoneNumber.replace(Regex("[^+0-9]"), "")
    }

    /**
     * Creates a unique identifier for this call session
     */
    fun getSessionId(): String {
        return "${callId}_${startTime.toString().replace(":", "-")}"
    }
}