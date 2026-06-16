package com.drp33.quietsignal.viewmodels

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
import com.drp33.quietsignal.util.MediaCache
import com.drp33.quietsignal.util.decodeSampledBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Two-way photo "snaps", shared by both roles. Sends from [selfId]'s camera and
 * shows [peerId]'s recent snaps (newest first, so the viewer can swipe back
 * through ones that arrived back-to-back). Mirrors [VoiceMessagingViewModel].
 */
class PhotoMessagingViewModel(
    private val repository: CheckInRepository,
    private val selfId: Int,
    private val peerId: Int,
) : ViewModel() {

    var state by mutableStateOf(PhotoMessagingState())
        private set

    init {
        // Pull any snaps already waiting, and refresh whenever the peer sends one.
        loadRecent(markNew = false)
        viewModelScope.launch {
            NotificationBus.events.collect { event ->
                if (event == "PHOTO_MESSAGE") loadRecent(markNew = true)
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
     * Fetch the peer's recent snaps (server enforces expiry), newest first, and
     * decode them all for the viewer. The count is the number waiting — derived
     * from the server list, so a missed push self-corrects on the next refresh.
     * [markNew] pops the notification banner; the silent startup load passes false.
     */
    fun loadRecent(markNew: Boolean = false) {
        viewModelScope.launch {
            repository.getRecentPhotos(peerId)
                .onSuccess { names ->
                    val bitmaps = names.mapNotNull { name -> decodeSnap(name) }
                    state = if (bitmaps.isEmpty()) {
                        // Nothing currently viewable (all expired) — dismiss.
                        state.copy(images = emptyList(), isNew = false)
                    } else {
                        state.copy(images = bitmaps, isNew = markNew || state.isNew)
                    }
                }
                .onFailure {
                    Log.d("Photo", "No current snaps from $peerId")
                }
        }
    }

    /** Fetch + decode one snap by object name, reusing the shared media cache. */
    private suspend fun decodeSnap(objectName: String): androidx.compose.ui.graphics.ImageBitmap? {
        MediaCache.get(objectName)?.let { return it }
        val bytes = MediaCache.getBytes(objectName)
            ?: repository.getMedia(objectName).getOrNull()?.also { MediaCache.putBytes(objectName, it) }
            ?: return null
        val bmp = withContext(Dispatchers.Default) { decodeSampledBitmap(bytes, 800, 800)?.asImageBitmap() }
        if (bmp != null) MediaCache.put(objectName, bmp)
        return bmp
    }

    /** Mark the snaps as seen so the notification dismisses. */
    fun markSeen() {
        state = state.copy(isNew = false, images = emptyList())
    }

    /** Dismiss the displayed snaps. */
    fun clear() {
        state = state.copy(images = emptyList(), isNew = false)
    }
}
