package com.drp33.quietsignal.model

import androidx.compose.ui.graphics.ImageBitmap

/** Recent received snaps (decoded for display) + a transient status line. */
data class PhotoMessagingState(
    /** Every currently-viewable snap from the peer, NEWEST FIRST. The viewer opens
     *  on the first (latest) and can page back through the rest. */
    val images: List<ImageBitmap> = emptyList(),
    val status: String = "",
    /** True when a snap just arrived (vs. ones loaded silently on startup). */
    val isNew: Boolean = false,
) {
    /** The latest snap — the banner thumbnail and the viewer's first page. */
    val image: ImageBitmap? get() = images.firstOrNull()

    /** How many snaps are waiting — drives "2 new photos". */
    val unreadCount: Int get() = images.size
}
