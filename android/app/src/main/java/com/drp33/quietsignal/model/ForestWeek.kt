package com.drp33.quietsignal.model

/**
 * One frozen weekly tree in the forest. [weekStart] is the Unix-second start of
 * the week (aligned to [WEEK_SECONDS]); [stage]/[deathLevel] are the real shared
 * tree state captured at that week's end. The week's memories are matched on the
 * client by grouping [MemoryItem.epoch] into the same week.
 */
data class ForestWeek(
    val weekStart: Long,
    val weekIndex: Int,
    val stage: Int,
    val deathLevel: Float,
)

/** One week, in seconds — MUST match the backend's WEEK_SECONDS.
 * DEMO VALUE: huge (~100 years) so a week never elapses during a demo — the tree
 * only grows and is never auto-frozen into the forest. Production: 7L * 24 * 3600. */
const val WEEK_SECONDS: Long = 3_153_600_000
