package com.drp33.quietsignal.data.remote.models

data class MemoriesResponse(
    val memories: List<MemoryDto> = emptyList(),
)

data class MemoryDto(
    val objectName: String = "",
    val type: String = "",
    val sender: String = "",
    val epoch: Long = 0,
)
