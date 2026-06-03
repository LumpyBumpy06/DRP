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

    init {
        // A push only reaches a device when the *peer* records, so any
        // VOICE_MESSAGE here means "the peer sent something".
        viewModelScope.launch {
            NotificationBus.events.collect { event ->
                if (event == "VOICE_MESSAGE") {
                    state = state.copy(hasNewMessage = true)
                }
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

    /** Fetch the peer's latest clip (server enforces the day-window expiry) and play it. */
    fun playLatest(play: (ByteArray) -> Unit) {
        viewModelScope.launch {
            state = state.copy(status = "Loading…")
            repository.getLatestVoice(peerId)
                .onSuccess { bytes ->
                    state = state.copy(status = "", hasNewMessage = false)
                    play(bytes)
                }
                .onFailure {
                    Log.e("Voice", "Failed to fetch voice message", it)
                    // 404 also means the message has expired past the day window.
                    state = state.copy(status = "No message right now", hasNewMessage = false)
                }
        }
    }
}
