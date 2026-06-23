package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorderHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun startRecording(): File? {
        try {
            val file = File(context.filesDir, "recording_${System.currentTimeMillis()}.mp4")
            currentFile = file

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            return file
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "Failed to start recording", e)
            return null
        }
    }

    fun stopRecording(): String? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            val path = currentFile?.absolutePath
            currentFile = null
            path
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "Failed to stop recording", e)
            mediaRecorder = null
            null
        }
    }
}

class AudioPlayerHelper {
    private var mediaPlayer: MediaPlayer? = null

    fun playAudio(path: String, onComplete: () -> Unit) {
        try {
            stopAudio()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    onComplete()
                    stopAudio()
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerHelper", "Failed to play audio", e)
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("AudioPlayerHelper", "Failed to stop audio", e)
        }
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
}
