package com.drp33.quietsignal.data.remote.models

data class ForestResponse(
    val weeks: List<ForestWeekResponse> = emptyList(),
)

data class ForestWeekResponse(
    val weekStart: Long = 0,
    val stage: Int = 0,
    val deathLevel: Float = 0f,
)
