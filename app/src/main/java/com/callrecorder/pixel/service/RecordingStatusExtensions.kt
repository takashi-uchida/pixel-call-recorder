package com.callrecorder.pixel.service

// Extensions to match test expectations without changing core APIs

val RecordingStatus.status: String
    get() = this.name

sealed class RecordingResultView {
    data class Success(
        val isSuccess: Boolean,
        val filePath: String,
        val duration: Long,
        val fileSize: Long
    ) : RecordingResultView()

    data class Error(
        val isSuccess: Boolean,
        val error: String
    ) : RecordingResultView()
}

val RecordingResult.isSuccess: Boolean
    get() = this is RecordingResult.Success

val RecordingResult.filePath: String
    get() = when (this) {
        is RecordingResult.Success -> this.recording.filePath
        is RecordingResult.Error -> ""
        RecordingResult.Cancelled -> ""
    }

val RecordingResult.duration: Long
    get() = when (this) {
        is RecordingResult.Success -> this.recording.duration
        is RecordingResult.Error -> 0L
        RecordingResult.Cancelled -> 0L
    }

val RecordingResult.fileSize: Long
    get() = when (this) {
        is RecordingResult.Success -> this.recording.fileSize
        is RecordingResult.Error -> 0L
        RecordingResult.Cancelled -> 0L
    }

val RecordingResult.error: String?
    get() = when (this) {
        is RecordingResult.Success -> null
        is RecordingResult.Error -> this.message
        RecordingResult.Cancelled -> "Cancelled"
    }

