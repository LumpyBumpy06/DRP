package com.drp33.quietsignal.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Thin wrapper around [MediaRecorder] that records a short voice clip to a cache
 * file and hands back the raw bytes when stopped.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start() {
        if (recorder != null) return

        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.aac")
        outputFile = file

        recorder = createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    /** Stops recording and returns the recorded bytes, or null on failure. */
    fun stop(): ByteArray? {
        val file = outputFile
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputFile = null
            file?.readBytes()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop recording", e)
            recorder?.release()
            recorder = null
            outputFile = null
            null
        }
    }

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
}
