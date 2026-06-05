package com.drp33.quietsignal.viewmodels

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.drp33.quietsignal.model.NotificationBus
import com.drp33.quietsignal.model.PhotoMessagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Two-way photo "snaps", shared by both roles. Sends from [selfId]'s camera and
 * shows [peerId]'s latest snap. Mirrors [VoiceMessagingViewModel].
 */
class PhotoMessagingViewModel(
    private val repository: CheckInRepository,
    private val selfId: Int,
    private val peerId: Int,
) : ViewModel() {

    var state by mutableStateOf(PhotoMessagingState())
        private set

    init {
        // Pull any snap already waiting, and refresh whenever the peer sends one.
        loadLatest()
        viewModelScope.launch {
            NotificationBus.events.collect { event ->
                if (event == "PHOTO_MESSAGE") loadLatest()
            }
        }
    }

    /** Upload a captured snap (JPEG bytes) to this user's mailbox. */
    fun sendPhoto(jpeg: ByteArray, onSent: () -> Unit = {}) {
        viewModelScope.launch {
            state = state.copy(status = "Sending snap…")
            repository.postPhoto(selfId, jpeg)
                .onSuccess {
                    state = state.copy(status = "")
                    onSent()
                }
                .onFailure {
                    Log.e("Photo", "Snap upload failed", it)
                    state = state.copy(status = "Couldn't send snap")
                }
        }
    }

    /** Fetch the peer's latest snap (server enforces expiry) and decode it for display. */
    fun loadLatest() {
        viewModelScope.launch {
            repository.getLatestPhoto(peerId)
                .onSuccess { bytes ->
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                    if (bitmap != null) state = state.copy(image = bitmap)
                }
                .onFailure {
                    // 404 = nothing recent; just leave the current state as-is.
                    Log.d("Photo", "No current snap from $peerId")
                }
        }
    }

    /** Dismiss the displayed snap. */
    fun clear() {
        state = state.copy(image = null)
    }
}
