package com.drp33.quietsignal.model

data class AdultState(
    val checkedIn: Boolean = false,
    val hasNewVoice: Boolean = false,
    val voiceStatus: String = ""
)