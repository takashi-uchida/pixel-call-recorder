package com.callrecorder.pixel.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Room Entity representing a call recording.
 * Stores all metadata and file information for recorded calls.
 */
@Entity(tableName = "recordings")
@TypeConverters(Recording.Converters::class)
@Parcelize
data class Recording(
    @PrimaryKey
    val id: String,
    val fileName: String,
    val filePath: String,
    val contactName: String?,
    val phoneNumber: String,
    val duration: Long, // Duration in milliseconds
    val fileSize: Long, // File size in bytes
    val recordingDate: LocalDateTime,
    val audioQuality: AudioQuality,
    val isIncoming: Boolean
) : Parcelable {

    /**
     * Validates the recording data
     */
    fun isValid(): Boolean {
        return id.isNotBlank() &&
               fileName.isNotBlank() &&
               filePath.isNotBlank() &&
               phoneNumber.isNotBlank() &&
               duration >= 0 &&
               fileSize >= 0
    }

    /**
     * Gets display name for the recording (contact name or phone number)
     */
    fun getDisplayName(): String {
        return contactName?.takeIf { it.isNotBlank() } ?: phoneNumber
    }

    /**
     * Gets formatted duration string (MM:SS)
     */
    fun getFormattedDuration(): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Gets formatted file size string
     */
    fun getFormattedFileSize(): String {
        return when {
            fileSize < 1024 -> "${fileSize}B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024}KB"
            else -> String.format("%.1fMB", fileSize / (1024.0 * 1024.0))
        }
    }

    /**
     * Gets formatted recording date string
     */
    fun getFormattedDate(): String {
        return recordingDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
    }

    /**
     * Gets call direction string for display
     */
    fun getCallDirection(): String {
        return if (isIncoming) "着信" else "発信"
    }

    /**
     * Type converters for Room database
     */
    class Converters {
        @TypeConverter
        fun fromLocalDateTime(dateTime: LocalDateTime?): String? {
            return dateTime?.toString()
        }

        @TypeConverter
        fun toLocalDateTime(dateTimeString: String?): LocalDateTime? {
            return dateTimeString?.let { LocalDateTime.parse(it) }
        }

        @TypeConverter
        fun fromAudioQuality(audioQuality: AudioQuality): String {
            return audioQuality.name
        }

        @TypeConverter
        fun toAudioQuality(audioQualityString: String): AudioQuality {
            return AudioQuality.valueOf(audioQualityString)
        }
    }
}