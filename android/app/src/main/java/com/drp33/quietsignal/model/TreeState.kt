package com.drp33.quietsignal.model

/** Shared motivation-tree state. growth/leafiness are 0..1; treeType selects the species. */
data class TreeState(
    val growth: Float = 0f,
    val leafiness: Float = 1f,
    val treeType: Int = 0,
    /** Number of shared check-in/voice moments — the canopy is built from these. */
    val memoryCount: Int = 0,
)
