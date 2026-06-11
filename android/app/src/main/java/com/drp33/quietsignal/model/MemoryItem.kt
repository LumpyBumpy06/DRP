package com.drp33.quietsignal.model

import androidx.compose.ui.graphics.ImageBitmap

/** One memory on the board: a voice memo or a snap. `image` is loaded lazily for photos. */
data class MemoryItem(
    val objectName: String,
    val type: String, // "photo" | "voice"
    val sender: String,
    val epoch: Long,
    val image: ImageBitmap? = null,
    /** Shared labels on this memory (e.g. "Favourites", "Family", custom). */
    val tags: List<String> = emptyList(),
)
