package com.drp33.quietsignal.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.drp33.quietsignal.data.SettingsPreferences
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import com.drp33.quietsignal.viewmodels.ThreadsViewModel

/**
 * Hosts the Gallery and Settings dialogs for a home screen. Kept here so both
 * [AdultScreen] and [ElderlyScreen] open them identically. Starting a thread
 * from the Gallery opens [ThreadChatScreen] via the shared [ThreadsViewModel]
 * (the chat overlay lives in [MainShell]).
 */
@Composable
fun GroveModals(
    showGallery: Boolean,
    onCloseGallery: () -> Unit,
    showSettings: Boolean,
    onCloseSettings: () -> Unit,
    memoriesVm: MemoriesViewModel,
    currentUserId: Int,
    threadsVm: ThreadsViewModel,
) {
    val context = LocalContext.current

    if (showGallery) {
        MemoriesDialog(
            vm = memoriesVm,
            currentUserId = currentUserId,
            onClose = onCloseGallery,
            onStartThread = { item ->
                threadsVm.openThread(item.objectName, item.type, item.sender)
                onCloseGallery()
            },
        )
    }

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
            onClose = onCloseSettings,
        )
    }
}
