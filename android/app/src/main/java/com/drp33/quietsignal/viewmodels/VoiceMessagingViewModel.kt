package com.drp33.quietsignal.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.drp33.quietsignal.model.NotificationBus
import com.drp33.quietsignal.model.VoiceMessagingState
import kotlinx.coroutines.launch

/**
 * Two-way voice messaging, shared by both roles. Records into [selfId]'s mailbox
 * and plays [peerId]'s latest clip — Norman uses (self=1, peer=2) and Sadie uses
 * (self=2, peer=1), so there's a single implementation for both directions.
 */
class VoiceMessagingViewModel(
    private val repository: CheckInRepository,
    private val selfId: Int,
    private val peerId: Int,
) : ViewModel() {

    var state by mutableStateOf(VoiceMessagingState())
        private set

    // The peer's latest clip, fetched by the availability check so playback is instant.
    private var latestBytes: ByteArray? = null

    init {
        // Fetch any existing message right away.
        checkLatest(markNew = false)

        // React instantly when a push says the peer just sent a clip...
        viewModelScope.launch {
            NotificationBus.events.collect { event ->
                if (event == "VOICE_MESSAGE") {
                    checkLatest(markNew = true)
                }
            }
        }
    }

    /** Is there a current clip from the peer? Caches it for instant playback. */
    private fun checkLatest(markNew: Boolean = false) {
        viewModelScope.launch {
            repository.getLatestVoice(peerId)
                .onSuccess { bytes ->
                    latestBytes = bytes
                    val nextUnread = if (markNew) state.unreadCount + 1 else state.unreadCount
                    state = state.copy(
                        available = true,
                        hasNewMessage = if (markNew) true else state.hasNewMessage,
                        unreadCount = nextUnread
                    )
                }
                .onFailure {
                    // 404 = nothing there, or the message expired.
                    latestBytes = null
                    state = state.copy(available = false, hasNewMessage = false, unreadCount = 0)
                }
        }
    }

    /** Upload a recording to this user's mailbox. */
    fun onRecorded(audio: ByteArray, onUploaded: () -> Unit = {}) {
        viewModelScope.launch {
            repository.postVoice(selfId, audio)
                .onSuccess {
                    Log.i("Voice", "Uploaded ${audio.size} bytes for user $selfId")
                    onUploaded()
                }
                .onFailure { Log.e("Voice", "Voice upload failed", it) }
        }
    }

    /** Play the peer's latest clip (already fetched by the availability check). */
    fun playLatest(play: (ByteArray) -> Unit) {
        val bytes = latestBytes
        if (bytes != null) {
            state = state.copy(status = "", hasNewMessage = false, unreadCount = 0)
            play(bytes)
        } else {
            state = state.copy(status = "No message right now", available = false)
        }
    }
}
