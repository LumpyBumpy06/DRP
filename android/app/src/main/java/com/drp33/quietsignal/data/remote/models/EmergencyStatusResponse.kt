package com.drp33.quietsignal.data.remote.models

data class EmergencyStatusResponse(
    val active: Boolean,
    val sender: String? = null,
)
