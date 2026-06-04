package com.drp33.quietsignal.model

/**
 * Shared watering-tree state. `stage` grows with the total number of waterings;
 * `deathLevel` (0..1) rises with neglect and wilts / regresses the tree.
 */
data class TreeState(
    val stage: Int = 0,
    val deathLevel: Float = 0f,
)
