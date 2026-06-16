package com.drp33.quietsignal.model

data class VoiceMessagingState(
    /** Every currently-playable clip from the peer, NEWEST FIRST. The player opens
     *  on the first (latest) and can step back through the rest. */
    val clips: List<String> = emptyList(),
    val hasNewMessage: Boolean = false,
    val available: Boolean = false,
    val status: String = "",
) {
    /** How many clips are waiting — drives "2 new voice messages". */
    val unreadCount: Int get() = clips.size
}
