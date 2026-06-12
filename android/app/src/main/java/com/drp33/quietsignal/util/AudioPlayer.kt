package com.drp33.quietsignal.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * Plays a voice clip (received as raw bytes) and exposes enough state to drive a
 * voice-message style player (duration, current position, play/pause, seek).
 */
class AudioPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    /** Writes the clip, starts playback and returns its duration in ms (0 on failure). */
    fun play(bytes: ByteArray, onCompletion: () -> Unit): Int {
        release()

        val file = File(context.cacheDir, "incoming_voice.m4a")
        file.writeBytes(bytes)

        return try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { onCompletion() }
                prepare()
                start()
            }
            player = mp
            mp.duration
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to play clip", e)
            release()
            0
        }
    }

    /** Reads a clip's duration (ms) without playing it — used to label voice bars. */
    fun durationOf(bytes: ByteArray): Int =
        try {
            val file = File(context.cacheDir, "probe_${bytes.size}.m4a")
            file.writeBytes(bytes)
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
            mmr.release()
            file.delete()
            ms
        } catch (e: Exception) {
            Log.w("AudioPlayer", "Failed to read duration", e)
            0
        }

    fun pause() = ignoreState { player?.pause() }

    fun resume() = ignoreState { player?.start() }

    fun seekTo(positionMs: Int) = ignoreState { player?.seekTo(positionMs) }

    fun position(): Int =
        try {
            player?.currentPosition ?: 0
        } catch (e: IllegalStateException) {
            0
        }

    fun release() {
        player?.release()
        player = null
    }

    private inline fun ignoreState(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalStateException) {
            Log.w("AudioPlayer", "Player not in a valid state", e)
        }
    }
}
