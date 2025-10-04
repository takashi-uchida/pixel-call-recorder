package com.callrecorder.pixel.data.model

/**
 * Enum representing different audio quality settings for call recordings.
 * Each quality level defines sample rate, bit rate, and channel configuration.
 */
enum class AudioQuality(
    val sampleRate: Int,
    val bitRate: Int,
    val channels: Int,
    val displayName: String
) {
    HIGH_QUALITY(48000, 128000, 2, "高品質"),
    STANDARD(44100, 64000, 1, "標準"),
    SPACE_SAVING(22050, 32000, 1, "省容量");

    /**
     * Validates if the audio quality settings are supported by the device
     */
    fun isSupported(): Boolean {
        return sampleRate > 0 && bitRate > 0 && channels in 1..2
    }

    /**
     * Gets the estimated file size per minute in bytes
     */
    fun getEstimatedFileSizePerMinute(): Long {
        return (bitRate * 60L) / 8L // Convert bits to bytes per minute
    }
}