package com.example.pixelcallrecorder.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileUtils {
    private const val RECORDINGS_DIR = "recordings"
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun getRecordingFilePath(context: Context): String {
        val recordingsDir = File(context.getExternalFilesDir(null), RECORDINGS_DIR)
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
        val timestamp = dateFormat.format(Date())
        return File(recordingsDir, "recording_$timestamp.3gp").absolutePath
    }

    fun getRecordingsList(context: Context): List<File> {
        val recordingsDir = File(context.getExternalFilesDir(null), RECORDINGS_DIR)
        return if (recordingsDir.exists()) {
            recordingsDir.listFiles()?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }
}
