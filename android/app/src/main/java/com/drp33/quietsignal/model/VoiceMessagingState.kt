package com.drp33.quietsignal.model

data class VoiceMessagingState(
    val hasNewMessage: Boolean = false,
    /** How many clips have arrived since the last one was opened. Lets the UI say
     * "2 new voice messages" when they come in back-to-back, and reset to 0 once
     * the listener opens them. */
    val unreadCount: Int = 0,
    val available: Boolean = false,
    val status: String = ""
)
