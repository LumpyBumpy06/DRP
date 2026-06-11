package com.drp33.quietsignal.model

import androidx.compose.ui.graphics.ImageBitmap

/** Latest received snap (decoded for display) + a transient status line. */
data class PhotoMessagingState(
    val image: ImageBitmap? = null,
    val status: String = "",
    /** True when the snap just arrived (vs. one loaded silently on startup). */
    val isNew: Boolean = false,
    /** How many snaps have arrived since the last one was opened, so the UI can
     * say "2 new photos" when they're sent back-to-back. Reset to 0 once viewed. */
    val unreadCount: Int = 0,
)
