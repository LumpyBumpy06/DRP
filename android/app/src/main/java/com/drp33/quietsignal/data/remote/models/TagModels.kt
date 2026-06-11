package com.drp33.quietsignal.data.remote.models

/** Every distinct tag name on the server (GET /tags). */
data class TagsResponse(
    val tags: List<String> = emptyList(),
)

/** The tag set on a single memory after a read/write (GET/POST /memory/tags). */
data class MemoryTagsResponse(
    val tags: List<String> = emptyList(),
)

/** Body for POST /memory/tags — the full replacement tag set for one memory. */
data class MemoryTagsRequest(
    val tags: List<String> = emptyList(),
)
