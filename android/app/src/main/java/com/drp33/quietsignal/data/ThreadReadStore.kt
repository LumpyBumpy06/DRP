package com.drp33.quietsignal.data

import android.content.Context

/**
 * Persistent per-thread read state. The server only reports the *total* number of
 * messages received from the partner (`incoming`), not how many are unread — so we
 * remember, per conversation, how many partner messages had been seen the last time
 * it was opened. Unread = total received − seen. Stored in shared prefs so the
 * badges survive sign-out and app restarts, and namespaced by the signed-in user.
 */
class ThreadReadStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("quietsignal_thread_reads", Context.MODE_PRIVATE)

    /** How many of this thread's partner messages have already been seen. */
    fun seenCount(selfId: Int, anchor: String): Int = prefs.getInt(key(selfId, anchor), 0)

    /** Record that [count] partner messages in this thread have now been seen. */
    fun setSeenCount(selfId: Int, anchor: String, count: Int) {
        prefs.edit().putInt(key(selfId, anchor), count).apply()
    }

    private fun key(selfId: Int, anchor: String) = "seen_${selfId}_$anchor"
}
