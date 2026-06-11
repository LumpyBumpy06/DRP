package com.drp33.quietsignal.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.drp33.quietsignal.data.SettingsPreferences
import com.drp33.quietsignal.viewmodels.ThreadsViewModel

/**
 * Hosts the Settings dialog for a home screen. Kept here so both [AdultScreen]
 * and [ElderlyScreen] open it identically. (The shared Gallery now opens from the
 * bottom nav in [MainShell].)
 */
@Composable
fun GroveModals(
    showSettings: Boolean,
    onCloseSettings: () -> Unit,
    threadsVm: ThreadsViewModel,
    onSwitchRole: () -> Unit = {},
) {
    val context = LocalContext.current

    if (showSettings) {
        var prompts by remember { mutableStateOf(SettingsPreferences.promptsEnabled(context)) }
        var freq by remember { mutableStateOf(SettingsPreferences.promptFrequency(context)) }
        var quiet by remember { mutableStateOf(SettingsPreferences.quietDelivery(context)) }
        var both by remember { mutableStateOf(SettingsPreferences.promptBoth(context)) }

        SettingsSheet(
            promptsEnabled = prompts,
            frequency = freq,
            quietDelivery = quiet,
            promptBoth = both,
            prompt = threadsVm.prompt,
            onPromptsChange = { prompts = it; SettingsPreferences.setPromptsEnabled(context, it) },
            onFrequencyChange = { freq = it; SettingsPreferences.setPromptFrequency(context, it) },
            onQuietDeliveryChange = { quiet = it; SettingsPreferences.setQuietDelivery(context, it) },
            onPromptBothChange = { both = it; SettingsPreferences.setPromptBoth(context, it) },
            onSignOut = { onSwitchRole(); onCloseSettings() },
            onClose = onCloseSettings,
        )
    }
}
