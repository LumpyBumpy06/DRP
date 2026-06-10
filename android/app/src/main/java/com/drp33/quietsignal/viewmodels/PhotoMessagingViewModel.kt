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
        loadLatest(markNew = false)
        viewModelScope.launch {
            NotificationBus.events.collect { event ->
                if (event == "PHOTO_MESSAGE") loadLatest(markNew = true)
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

    /**
     * Fetch the peer's latest snap (server enforces expiry) and decode it for display.
     * [markNew] flags it as a fresh arrival so the UI can pop a notification banner;
     * the silent startup load passes false.
     */
    fun loadLatest(markNew: Boolean = false) {
        viewModelScope.launch {
            repository.getLatestPhoto(peerId)
                .onSuccess { bytes ->
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                    if (bitmap != null) {
                        // Count back-to-back snaps so the viewer sees how many
                        // arrived before they opened them.
                        val nextUnread = if (markNew) state.unreadCount + 1 else state.unreadCount
                        state = state.copy(image = bitmap, isNew = markNew, unreadCount = nextUnread)
                    }
                }
                .onFailure {
                    // 404 = nothing recent; just leave the current state as-is.
                    Log.d("Photo", "No current snap from $peerId")
                }
        }
    }

    /** Mark the current snap as seen so its notification dismisses. */
    fun markSeen() {
        state = state.copy(isNew = false, unreadCount = 0)
    }

    /** Dismiss the displayed snap. */
    fun clear() {
        state = state.copy(image = null, isNew = false)
    }
}
