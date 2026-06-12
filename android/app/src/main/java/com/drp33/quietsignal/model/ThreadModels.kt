package com.drp33.quietsignal.model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * One conversation in the Threads list — every chat hangs off a memory
 * (`anchor` is that photo/voice memo's object name). Both partners share the
 * same thread, so a reply from either side lands here.
 */
data class ThreadSummary(
    val anchor: String,
    val memoryType: String,   // "photo" | "voice" — what the thread is about
    val memorySender: String,
    val memoryObject: String, // the storage object behind the anchor (for media)
    val isPrompt: Boolean,    // true for a prompt conversation (✨ styling)
    val caption: String,      // user-given title, persisted on the server
    val count: Int,
    val incoming: Int,        // messages from the partner (soft unread hint)
    val lastKind: String,     // "text" | "voice" | "photo"
    val lastText: String,
    val lastSenderId: Int,
    val lastSender: String,
    val lastEpoch: Long,
    val image: ImageBitmap? = null,
)

/** One message inside a thread. `image` is decoded lazily for photo messages. */
data class ThreadMessage(
    val id: Long,
    val anchor: String,
    val senderId: Int,
    val sender: String,
    val kind: String,         // "text" | "voice" | "photo"
    val text: String,
    val mediaObject: String?,
    val epoch: Long,
    val image: ImageBitmap? = null,
)

/** A memory the tree gently resurfaces when things go quiet (the prompt).
 * [threadAnchor] is the per-prompt chat anchor — each new prompt gets a FRESH
 * conversation, separate from earlier prompts about the same memory. */
data class PromptMemory(
    val objectName: String,
    val type: String,         // "photo" | "voice"
    val sender: String,
    val epoch: Long,
    val threadAnchor: String,
)

/** The storage object a thread anchor points at (strips the prompt wrapper). */
fun threadMediaObject(anchor: String): String =
    if (anchor.startsWith("prompt/")) anchor.split("/", limit = 3).getOrNull(2) ?: anchor
    else anchor
