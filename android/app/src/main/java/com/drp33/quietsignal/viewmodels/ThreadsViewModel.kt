package com.drp33.quietsignal.viewmodels

import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.drp33.quietsignal.model.PromptMemory
import com.drp33.quietsignal.model.ThreadMessage
import com.drp33.quietsignal.model.ThreadSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the Threads tab and the chat view. A "thread" is every message that
 * shares an `anchor` (the object name of the photo/voice memo it's about), so
 * both Norman (self=1) and Sadie (self=2) read and write the same conversation.
 */
class ThreadsViewModel(
    private val repository: CheckInRepository,
    val selfId: Int,
) : ViewModel() {

    var summaries by mutableStateOf<List<ThreadSummary>>(emptyList())
        private set
    var prompt by mutableStateOf<PromptMemory?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    // Currently-open conversation.
    var activeAnchor by mutableStateOf<String?>(null)
        private set
    var activeType by mutableStateOf("photo")
        private set
    var activeSender by mutableStateOf("")
        private set
    var activeIsPrompt by mutableStateOf(false)
        private set
    var messages by mutableStateOf<List<ThreadMessage>>(emptyList())
        private set

    /** Refresh the conversation list (and the gentle prompt suggestion). */
    fun loadThreads() {
        viewModelScope.launch {
            loading = true
            repository.getThreads(selfId).onSuccess { summaries = it }
            loading = false
        }
        loadPrompt()
    }

    fun loadPrompt() {
        viewModelScope.launch {
            repository.getPrompt().onSuccess { prompt = it }
        }
    }

    /** Open the conversation hanging off [anchor], decoding any photo messages. */
    fun openThread(anchor: String, type: String, sender: String, isPrompt: Boolean = false) {
        activeAnchor = anchor
        activeType = type
        activeSender = sender
        activeIsPrompt = isPrompt
        messages = emptyList()
        reloadMessages()
    }

    fun closeThread() {
        activeAnchor = null
        messages = emptyList()
        activeIsPrompt = false
        loadThreads() // refresh list + badges
    }

    private fun reloadMessages() {
        val anchor = activeAnchor ?: return
        viewModelScope.launch {
            repository.getThread(anchor).onSuccess { items ->
                messages = items
                // Decode photo-message thumbnails in the background.
                items.filter { it.kind == "photo" && it.mediaObject != null }.forEach { msg ->
                    repository.getMedia(msg.mediaObject!!).onSuccess { bytes ->
                        val bmp = withContext(Dispatchers.Default) {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }
                        if (bmp != null) {
                            messages = messages.map { if (it.id == msg.id) it.copy(image = bmp) else it }
                        }
                    }
                }
            }
        }
    }

    fun sendText(text: String) {
        val anchor = activeAnchor ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.postThreadText(anchor, selfId, text.trim()).onSuccess { reloadMessages() }
        }
    }

    fun sendVoice(audio: ByteArray) {
        val anchor = activeAnchor ?: return
        viewModelScope.launch {
            repository.postThreadVoice(anchor, selfId, audio).onSuccess { reloadMessages() }
        }
    }

    fun sendPhoto(jpeg: ByteArray) {
        val anchor = activeAnchor ?: return
        viewModelScope.launch {
            repository.postThreadPhoto(anchor, selfId, jpeg).onSuccess { reloadMessages() }
        }
    }

    /** Fetch raw bytes (e.g. to play a voice message). */
    fun loadMediaBytes(objectName: String, onBytes: (ByteArray) -> Unit) {
        viewModelScope.launch {
            repository.getMedia(objectName).onSuccess(onBytes)
        }
    }
}
