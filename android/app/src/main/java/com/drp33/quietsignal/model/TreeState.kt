package com.drp33.quietsignal.model

/**
 * Shared watering-tree state. `stage` grows with the total number of waterings;
 * `deathLevel` (0..1) rises with neglect and wilts / regresses the tree.
 * `momentCount` is the number of photos/voice notes stored on the live tree,
 * tracked server-side and reset to 0 when the tree is added to the forest.
 */
data class TreeState(
    val stage: Int = 0,
    val deathLevel: Float = 0f,
    val momentCount: Int = 0,
)
