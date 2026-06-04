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

    /**
     * Starts recording. Returns true on success; false (without crashing) if the
     * mic can't be accessed — e.g. permission denied, the OS mic toggle is off, or
     * no mic is available.
     */
    fun start(): Boolean {
        if (recorder != null) return true

        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")

        return try {
            val r = createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = r
            outputFile = file
            true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            runCatching { recorder?.release() }
            recorder = null
            outputFile = null
            false
        }
    }

    /**
     * Peak microphone amplitude since the previous call (0..32767), or 0 when not
     * recording. Used to drive a live, speech-reactive visualisation.
     */
    fun amplitude(): Int =
        try {
            recorder?.maxAmplitude ?: 0
        } catch (e: IllegalStateException) {
            0
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
