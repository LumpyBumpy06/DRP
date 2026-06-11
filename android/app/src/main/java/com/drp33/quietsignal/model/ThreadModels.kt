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
    val count: Int,
    val incoming: Int,        // messages from the partner (soft unread hint)
    val lastKind: String,     // "text" | "voice" | "photo"
    val lastText: String,
    val lastSenderId: Int,
    val lastSender: String,
    val lastEpoch: Long,
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

/** A memory the tree gently resurfaces when things go quiet (the prompt). */
data class PromptMemory(
    val objectName: String,
    val type: String,         // "photo" | "voice"
    val sender: String,
    val epoch: Long,
)
