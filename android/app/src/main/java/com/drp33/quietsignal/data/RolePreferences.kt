package com.drp33.quietsignal.data

import android.content.Context
import com.drp33.quietsignal.model.UserRole

/** Remembers which role was chosen so the app reopens on that screen. */
object RolePreferences {
    private const val PREFS = "quietsignal_prefs"
    private const val KEY_ROLE = "selected_role"

    fun save(context: Context, role: UserRole) {
        prefs(context).edit().putString(KEY_ROLE, role.name).apply()
    }

    fun get(context: Context): UserRole? {
        val name = prefs(context).getString(KEY_ROLE, null) ?: return null
        return runCatching { UserRole.valueOf(name) }.getOrNull()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_ROLE).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
