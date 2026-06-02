package com.drp33.quietsignal.util

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * Plays a voice clip (received as raw bytes) by writing it to a cache file and
 * handing it to [MediaPlayer]. One clip at a time — starting a new one stops the
 * previous.
 */
class AudioPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    fun play(bytes: ByteArray) {
        release()

        val file = File(context.cacheDir, "incoming_voice.m4a")
        file.writeBytes(bytes)

        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { release() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to play clip", e)
            release()
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
