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
 * and plays [peerId]'s recent clips (newest first, so the listener can step back
 * through ones that arrived back-to-back). Norman uses (self=1, peer=2) and Sadie
 * uses (self=2, peer=1), so there's a single implementation for both directions.
 */
class VoiceMessagingViewModel(
    private val repository: CheckInRepository,
    private val selfId: Int,
    private val peerId: Int,
) : ViewModel() {

    var state by mutableStateOf(VoiceMessagingState())
        private set

    init {
        // Fetch any existing clips right away.
        loadRecent(markNew = false)

        // React instantly when a push says the peer just sent a clip.
        viewModelScope.launch {
            NotificationBus.events.collect { event ->
                if (event == "VOICE_MESSAGE") loadRecent(markNew = true)
            }
        }
    }

    /**
     * Fetch the peer's recent clips (server enforces expiry), newest first. The
     * count is derived from this server list, so a missed push self-corrects on
     * the next refresh. [markNew] pops the notification; startup passes false.
     */
    fun loadRecent(markNew: Boolean = false) {
        viewModelScope.launch {
            repository.getRecentVoices(peerId)
                .onSuccess { names ->
                    state = if (names.isEmpty()) {
                        state.copy(clips = emptyList(), available = false, hasNewMessage = false)
                    } else {
                        state.copy(clips = names, available = true, hasNewMessage = markNew || state.hasNewMessage)
                    }
                }
                .onFailure {
                    // 404 / nothing there — or every clip expired.
                    state = state.copy(clips = emptyList(), available = false, hasNewMessage = false)
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

    /** Fetch + hand the bytes of the clip at [index] (0 = latest) to [play]. */
    fun playClip(index: Int, play: (ByteArray) -> Unit) {
        val names = state.clips
        if (index !in names.indices) return
        viewModelScope.launch {
            repository.getMedia(names[index])
                .onSuccess { bytes -> play(bytes) }
                .onFailure { state = state.copy(status = "Couldn't play that clip") }
        }
    }

    /** Dismiss the "new clip" notification (the clips stay available in the player). */
    fun markSeen() {
        state = state.copy(hasNewMessage = false)
    }
}
