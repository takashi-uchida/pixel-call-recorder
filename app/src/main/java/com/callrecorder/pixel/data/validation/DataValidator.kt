package com.callrecorder.pixel.data.validation

import com.callrecorder.pixel.data.model.CallInfo
import com.callrecorder.pixel.data.model.Recording
import java.io.File
import java.time.LocalDateTime

/**
 * Utility class for validating data models and their constraints.
 * Provides comprehensive validation for call recording data.
 */
object DataValidator {

    /**
     * Validates CallInfo data
     */
    fun validateCallInfo(callInfo: CallInfo): ValidationResult {
        val errors = mutableListOf<String>()

        if (callInfo.callId.isBlank()) {
            errors.add("Call ID cannot be empty")
        }

        if (callInfo.phoneNumber.isBlank()) {
            errors.add("Phone number cannot be empty")
        } else if (!isValidPhoneNumber(callInfo.phoneNumber)) {
            errors.add("Invalid phone number format")
        }

        if (callInfo.startTime.isAfter(LocalDateTime.now())) {
            errors.add("Start time cannot be in the future")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }

    /**
     * Validates Recording data
     */
    fun validateRecording(recording: Recording): ValidationResult {
        val errors = mutableListOf<String>()

        if (recording.id.isBlank()) {
            errors.add("Recording ID cannot be empty")
        }

        if (recording.fileName.isBlank()) {
            errors.add("File name cannot be empty")
        } else if (!isValidFileName(recording.fileName)) {
            errors.add("Invalid file name format")
        }

        if (recording.filePath.isBlank()) {
            errors.add("File path cannot be empty")
        }

        if (recording.phoneNumber.isBlank()) {
            errors.add("Phone number cannot be empty")
        } else if (!isValidPhoneNumber(recording.phoneNumber)) {
            errors.add("Invalid phone number format")
        }

        if (recording.duration < 0) {
            errors.add("Duration cannot be negative")
        }

        if (recording.fileSize < 0) {
            errors.add("File size cannot be negative")
        }

        if (recording.recordingDate.isAfter(LocalDateTime.now())) {
            errors.add("Recording date cannot be in the future")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }

    /**
     * Validates if a phone number has a valid format
     */
    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // Allow digits, spaces, hyphens, parentheses, and plus sign
        val phoneRegex = Regex("^[+]?[0-9\\-\\s()]+$")
        return phoneNumber.matches(phoneRegex) && 
               phoneNumber.replace(Regex("[^0-9]"), "").length >= 7
    }

    /**
     * Validates if a file name is valid
     */
    private fun isValidFileName(fileName: String): Boolean {
        // Check for invalid characters in file names
        val invalidChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return !fileName.any { it in invalidChars } && 
               fileName.trim() == fileName &&
               fileName.isNotEmpty()
    }

    /**
     * Validates if a file path exists and is accessible
     */
    fun validateFilePath(filePath: String): ValidationResult {
        return try {
            val file = File(filePath)
            when {
                !file.exists() -> ValidationResult.Error(listOf("File does not exist: $filePath"))
                !file.canRead() -> ValidationResult.Error(listOf("File is not readable: $filePath"))
                file.length() == 0L -> ValidationResult.Error(listOf("File is empty: $filePath"))
                else -> ValidationResult.Success
            }
        } catch (e: Exception) {
            ValidationResult.Error(listOf("Error accessing file: ${e.message}"))
        }
    }

    /**
     * Validates storage space requirements
     */
    fun validateStorageSpace(requiredBytes: Long, availableBytes: Long): ValidationResult {
        return if (availableBytes >= requiredBytes) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(listOf("Insufficient storage space. Required: ${requiredBytes}B, Available: ${availableBytes}B"))
        }
    }
}

/**
 * Sealed class representing validation results
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val errors: List<String>) : ValidationResult()

    fun isValid(): Boolean = this is Success
    
    fun getErrorMessage(): String? = when (this) {
        is Success -> null
        is Error -> errors.joinToString("; ")
    }
}