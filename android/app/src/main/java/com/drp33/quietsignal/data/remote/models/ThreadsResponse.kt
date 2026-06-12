package com.drp33.quietsignal.data.remote.models

/* DTOs for memory threads (conversations anchored to a photo / voice memo) and
   the gentle "prompt" memory. Field names match the FastAPI JSON keys 1:1 so
   Gson maps them directly. */

data class ThreadsResponse(
    val threads: List<ThreadSummaryDto> = emptyList(),
)

data class ThreadSummaryDto(
    val anchor: String = "",
    val memoryType: String = "",
    val memorySender: String = "",
    val memoryObject: String = "",
    val isPrompt: Boolean = false,
    val caption: String = "",
    val count: Int = 0,
    val incoming: Int = 0,
    val lastKind: String = "",
    val lastText: String = "",
    val lastSender: String = "",
    val lastSenderId: Int = 0,
    val lastEpoch: Long = 0,
)

data class ThreadMessagesResponse(
    val messages: List<ThreadMessageDto> = emptyList(),
)

data class ThreadMessageDto(
    val id: Long = 0,
    val anchor: String = "",
    val senderId: Int = 0,
    val sender: String = "",
    val kind: String = "",
    val text: String = "",
    val mediaObject: String? = null,
    val epoch: Long = 0,
)

/** Body for POST /thread/text. `user_id` is snake_case to match the FastAPI model. */
data class ThreadTextRequest(
    val anchor: String,
    val user_id: Int,
    val text: String,
)

data class PromptResponse(
    val prompt: PromptDto? = null,
)

data class PromptDto(
    val objectName: String = "",
    val type: String = "",
    val sender: String = "",
    val epoch: Long = 0,
    val threadAnchor: String = "",
)
