package com.drp33.quietsignal.model

/**
 * One frozen tree in the forest. [weekStart] is the Unix-second time it was
 * added (used for the date label and ordering); [stage]/[deathLevel] are the
 * shared tree state captured at that moment. The memories it holds are the ones
 * whose [MemoryItem.epoch] falls in [[periodStart], [periodEnd]) — the window of
 * moments shared since the previous add. [momentCount] is how many that was.
 */
data class ForestWeek(
    val weekStart: Long,
    val weekIndex: Int,
    val stage: Int,
    val deathLevel: Float,
    val periodStart: Long = 0,
    val periodEnd: Long = 0,
    val momentCount: Int = 0,
)

/** One week, in seconds — MUST match the backend's WEEK_SECONDS.
 * TEST VALUE: 60s so new trees appear ~every minute. Production: 7L * 24 * 3600. */
const val WEEK_SECONDS: Long = 600
