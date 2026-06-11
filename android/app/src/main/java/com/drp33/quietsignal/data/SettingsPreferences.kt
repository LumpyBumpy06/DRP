package com.drp33.quietsignal.data

import android.content.Context

/**
 * Local on/off settings for the Settings screen. Currently just the gentle
 * conversation-prompts toggle (and its frequency). Stored in the same shared
 * prefs file as [RolePreferences].
 */
object SettingsPreferences {
    private const val PREFS = "quietsignal_prefs"
    private const val KEY_PROMPTS = "prompts_enabled"
    private const val KEY_PROMPT_FREQ = "prompt_frequency"
    private const val KEY_PROMPT_BOTH = "prompt_both"
    private const val KEY_QUIET_DELIVERY = "quiet_delivery"

    fun promptsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROMPTS, true)

    fun setPromptsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROMPTS, enabled).apply()
    }

    /** "gentle" | "weekly" | "very_quiet" */
    fun promptFrequency(context: Context): String =
        prefs(context).getString(KEY_PROMPT_FREQ, "gentle") ?: "gentle"

    fun setPromptFrequency(context: Context, value: String) {
        prefs(context).edit().putString(KEY_PROMPT_FREQ, value).apply()
    }

    fun promptBoth(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROMPT_BOTH, true)

    fun setPromptBoth(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROMPT_BOTH, enabled).apply()
    }

    fun quietDelivery(context: Context): Boolean =
        prefs(context).getBoolean(KEY_QUIET_DELIVERY, true)

    fun setQuietDelivery(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_QUIET_DELIVERY, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
