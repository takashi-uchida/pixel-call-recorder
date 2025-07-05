package com.example.pixelcallrecorder.service

import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.example.pixelcallrecorder.utils.FileUtils
import java.io.IOException
import java.util.Date

class CallRecorderService : Service() {
    private lateinit var telephonyManager: TelephonyManager
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFilePath: String? = null

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> startRecording()
                TelephonyManager.CALL_STATE_IDLE -> stopRecording()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecording() {
        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recordingFilePath = FileUtils.getRecordingFilePath(this@CallRecorderService)
                setOutputFile(recordingFilePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                prepare()
                start()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaRecorder = null
    }

    override fun onDestroy() {
        super.onDestroy()
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
    }
}