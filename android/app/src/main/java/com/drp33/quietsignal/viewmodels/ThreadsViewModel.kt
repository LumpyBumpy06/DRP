package com.drp33.quietsignal.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.data.ThreadReadStore
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.drp33.quietsignal.model.NotificationBus
import com.drp33.quietsignal.model.PromptMemory
import com.drp33.quietsignal.model.ThreadMessage
import com.drp33.quietsignal.model.ThreadSummary
import com.drp33.quietsignal.model.threadMediaObject
import com.drp33.quietsignal.util.MediaCache
import com.drp33.quietsignal.util.decodeSampledBitmap
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
    private val readStore: ThreadReadStore,
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
    var messages by mutableStateOf<List<ThreadMessage>>(emptyList())
        private set

    init {
        // Event-driven sync (no polling): when the partner sends a message the
        // server pushes THREAD_MESSAGE, which we react to here. The UI also calls
        // syncNow() when the app returns to the foreground, covering any push that
        // was missed while backgrounded.
        viewModelScope.launch {
            NotificationBus.events.collect { event ->
                if (event == "THREAD_MESSAGE") syncNow()
            }
        }
    }

    /** Refresh whatever the user is currently looking at: the open chat's messages,
     *  or otherwise the thread list (which drives the unread badge). Triggered by a
     *  thread push and on app-foreground — not on a timer. */
    fun syncNow() {
        if (activeAnchor != null) reloadMessages() else loadThreads()
    }

    /** User-given captions per anchor — the conversation's title, set when a
     *  thread is first started. Kept in memory so it shows throughout the app
     *  (chat header, pinned memory, threads list). */
    private val captions = mutableStateMapOf<String, String>()

    /** The title for a conversation: its caption if one was given, otherwise the
     *  gentle default ("Sadie's photo" / "Norman's voice note"). */
    fun threadTitle(anchor: String, sender: String, type: String): String =
        captions[anchor]?.takeIf { it.isNotBlank() }
            ?: "${sender}'s ${if (type == "photo") "photo" else "voice note"}"

    /** How many of each thread's partner messages have already been seen. Backed
     *  by [readStore] (persisted across sign-out) but mirrored here as Compose
     *  state so the badges recompose when a thread is opened. */
    private val seenCounts = mutableStateMapOf<String, Int>()

    private fun seenCountFor(anchor: String): Int =
        seenCounts.getOrPut(anchor) { readStore.seenCount(selfId, anchor) }

    /** Mark a thread read: every partner message currently in it is now seen. */
    private fun markRead(anchor: String, partnerMessages: Int) {
        seenCounts[anchor] = partnerMessages
        readStore.setSeenCount(selfId, anchor, partnerMessages)
    }

    /** Unread = partner messages received minus those already seen (never below 0). */
    fun unreadFor(summary: ThreadSummary): Int =
        (summary.incoming - seenCountFor(summary.anchor)).coerceAtLeast(0)

    /** Total unread across all threads (drives the Threads tab badge). */
    val unreadTotal: Int get() = summaries.sumOf { unreadFor(it) }

    /** Whether a conversation already exists for this memory (its object name is
     *  the thread anchor). Lets the gallery say "Continue" and skip the caption. */
    fun hasThread(anchor: String): Boolean = summaries.any { it.anchor == anchor }

    /** Refresh the conversation list (and the gentle prompt suggestion). */
    fun loadThreads() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            repository.getThreads(selfId).onSuccess { items ->
                summaries = items
                // Hydrate titles from the server (captions survive restarts).
                items.forEach { if (it.caption.isNotBlank()) captions[it.anchor] = it.caption }

                // Decode anchored-memory thumbnails in parallel batches of 5.
                // Media lives under memoryObject (prompt anchors wrap it).
                val allNewBitmaps = mutableMapOf<String, ImageBitmap>()
                items.filter { it.memoryType == "photo" }.chunked(5).forEach { chunk ->
                    val bitmaps = chunk.mapNotNull { summary ->
                        val obj = summary.memoryObject
                        // 1. Check bitmap cache
                        MediaCache.get(obj)?.let { return@mapNotNull summary.anchor to it }

                        // 2. Check bytes cache, or fetch from network
                        val bytes = MediaCache.getBytes(obj)
                            ?: repository.getMedia(obj).getOrNull()?.also {
                                MediaCache.putBytes(obj, it)
                            }

                        if (bytes != null) {
                            val bmp = withContext(Dispatchers.Default) {
                                decodeSampledBitmap(bytes, 300, 300)?.asImageBitmap()
                            }
                            if (bmp != null) {
                                MediaCache.put(obj, bmp)
                                summary.anchor to bmp
                            } else null
                        } else null
                    }.toMap()

                    if (bitmaps.isNotEmpty()) {
                        allNewBitmaps.putAll(bitmaps)
                        summaries = summaries.map {
                            allNewBitmaps[it.anchor]?.let { bmp -> it.copy(image = bmp) } ?: it
                        }
                    }
                }
            }
            loading = false
        }
        loadPrompt()
    }

    fun loadPrompt() {
        viewModelScope.launch {
            repository.getPrompt().onSuccess { prompt = it }
        }
    }

    // Prompt anchors we've already asked the server to announce, so showing the
    // card repeatedly doesn't re-hit the network (the server dedupes too).
    private val announcedPrompts = mutableSetOf<String>()

    /** Tell the server the prompt card is on screen, so it nudges both partners
     *  once. Safe to call on every display — guarded here and on the server. */
    fun announcePrompt(promptKey: String) {
        if (!announcedPrompts.add(promptKey)) return
        viewModelScope.launch { repository.announcePrompt(promptKey) }
    }

    /** The storage object behind the open thread's anchor (prompt anchors wrap
     *  the memory's object name). Use this for the pinned media, not the anchor. */
    val activeMediaObject: String? get() = activeAnchor?.let { threadMediaObject(it) }

    /** Open the conversation hanging off [anchor], decoding any photo messages. */
    fun openThread(anchor: String, type: String, sender: String, title: String? = null) {
        if (!title.isNullOrBlank()) {
            captions[anchor] = title.trim()
            // Persist the title server-side so it survives app restarts. Passing
            // selfId lets the server water the tree when this starts a prompt
            // conversation (it ignores the id for ordinary gallery threads).
            viewModelScope.launch { repository.setThreadCaption(anchor, title.trim(), selfId) }
        }
        activeAnchor = anchor
        activeType = type
        activeSender = sender
        messages = emptyList()
        reloadMessages()
    }

    fun closeThread() {
        activeAnchor = null
        messages = emptyList()
        loadThreads() // refresh list + badges
    }


    private fun reloadMessages() {
        val anchor = activeAnchor ?: return
        viewModelScope.launch {
            repository.getThread(anchor).onSuccess { items ->
                messages = items
                // Opening (and watching) the thread marks all its partner messages
                // as read, so the badge clears and only NEW arrivals count next time.
                markRead(anchor, items.count { it.senderId != selfId })
                
                // Decode photo-message thumbnails in parallel batches of 5
                val allNewBitmaps = mutableMapOf<Long, ImageBitmap>()
                items.filter { it.kind == "photo" && it.mediaObject != null }.chunked(5).forEach { chunk ->
                    val bitmaps = chunk.mapNotNull { msg ->
                        val objectName = msg.mediaObject!!
                        // 1. Check bitmap cache (note: using id as key here for the UI list)
                        MediaCache.get(objectName)?.let { return@mapNotNull msg.id to it }

                        // 2. Check bytes cache, or fetch from network
                        val bytes = MediaCache.getBytes(objectName)
                            ?: repository.getMedia(objectName).getOrNull()?.also {
                                MediaCache.putBytes(objectName, it)
                            }

                        if (bytes != null) {
                            val bmp = withContext(Dispatchers.Default) {
                                decodeSampledBitmap(bytes, 400, 400)?.asImageBitmap()
                            }
                            if (bmp != null) {
                                MediaCache.put(objectName, bmp)
                                msg.id to bmp
                            } else null
                        } else null
                    }.toMap()

                    if (bitmaps.isNotEmpty()) {
                        allNewBitmaps.putAll(bitmaps)
                        messages = messages.map {
                            allNewBitmaps[it.id]?.let { bmp -> it.copy(image = bmp) } ?: it
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

    /** Decode a memory's photo to a bitmap (for the prompt caption preview), so
     *  the user can see the picture they're about to caption. Cached like the
     *  thread thumbnails. */
    fun loadPromptImage(objectName: String, onLoaded: (ImageBitmap?) -> Unit) {
        MediaCache.get(objectName)?.let { onLoaded(it); return }
        viewModelScope.launch {
            val bytes = MediaCache.getBytes(objectName)
                ?: repository.getMedia(objectName).getOrNull()?.also { MediaCache.putBytes(objectName, it) }
            if (bytes == null) {
                onLoaded(null)
                return@launch
            }
            val bmp = withContext(Dispatchers.Default) { decodeSampledBitmap(bytes, 800, 800)?.asImageBitmap() }
            if (bmp != null) MediaCache.put(objectName, bmp)
            onLoaded(bmp)
        }
    }

    /** Fetch raw bytes (e.g. to play a voice message). */
    fun loadMediaBytes(objectName: String, onBytes: (ByteArray) -> Unit) {
        MediaCache.getBytes(objectName)?.let { onBytes(it); return }
        viewModelScope.launch {
            repository.getMedia(objectName).onSuccess { bytes ->
                MediaCache.putBytes(objectName, bytes)
                onBytes(bytes)
            }
        }
    }
}
