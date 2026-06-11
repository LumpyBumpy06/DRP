package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.data.SettingsPreferences
import com.drp33.quietsignal.model.WEEK_SECONDS
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.Newsreader
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.viewmodels.AdultViewModel
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import com.drp33.quietsignal.viewmodels.PhotoMessagingViewModel
import com.drp33.quietsignal.viewmodels.ThreadsViewModel
import com.drp33.quietsignal.viewmodels.TreeViewModel
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel

/**
 * Sadie's home — the "This week" tab. Grove layout: header + quiet-safety strip,
 * the living [WateringTree] (kept exactly as before), a prompt line, and the
 * Voice · Photo · Hello · Note input row. Gallery + Settings open from the top
 * row; the gentle prompt card appears when the tree is quiet.
 */
@Composable
fun AdultScreen(
    viewModel: AdultViewModel,
    treeVm: TreeViewModel,
    voiceVm: VoiceMessagingViewModel,
    photoVm: PhotoMessagingViewModel,
    memoriesVm: MemoriesViewModel,
    threadsVm: ThreadsViewModel,
    contentPadding: PaddingValues = PaddingValues(),
    onSwitchRole: () -> Unit = {},
    onAllGood: () -> Unit = {},
) {
    val context = LocalContext.current
    val tree = treeVm.state
    val mood = treeMoodOf(tree.deathLevel)

    LaunchedEffect(Unit) {
        memoriesVm.load()
        threadsVm.loadThreads() // threads + the gentle prompt suggestion
    }
    val weekStart = (System.currentTimeMillis() / 1000 / WEEK_SECONDS) * WEEK_SECONDS
    val momentCount = memoriesVm.memories.count { (it.epoch / WEEK_SECONDS) * WEEK_SECONDS == weekStart }
    val lastMomentEpoch = memoriesVm.memories.maxOfOrNull { it.epoch }

    var showSettings by remember { mutableStateOf(false) }

    val promptsOn = remember(showSettings) { SettingsPreferences.promptsEnabled(context) }
    val prompt = threadsVm.prompt
    val showPrompt = promptsOn && mood == TreeMood.FADING && prompt != null

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroveCircleButton(glyph = "⚙️", contentDescription = "Settings", onClick = { showSettings = true })
            }

            GroveHeader(title = "Our garden", subtitle = "A living record of staying close.", momentCount = momentCount)

            Spacer(Modifier.height(12.dp))
            SafetyStrip(
                mood = mood,
                peerName = "Dad",
                peerLastSeenToday = viewModel.state.checkedIn,
                lastMomentEpoch = lastMomentEpoch
            )

            // Living tree — fills the middle. Untouched WateringTree (tree.json).
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                WateringTree(stage = tree.stage, deathLevel = tree.deathLevel)
            }

            if (showPrompt && prompt != null) {
                PromptCard(
                    title = "It's been quiet — remember this?",
                    subtitle = "A ${if (prompt.type == "photo") "photo" else "voice note"} from ${prompt.sender}",
                    onClick = { threadsVm.openThread(prompt.objectName, prompt.type, prompt.sender, isPrompt = true) },
                )
            } else {
                Text(
                    text = "Add a moment to help it grow",
                    fontFamily = NunitoSans,
                    fontSize = 13.5.sp,
                    color = Grove.InkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))

            GroveInputRow(
                onVoiceRecorded = { voiceVm.onRecorded(it, onUploaded = { treeVm.refresh() }) },
                onPhotoCaptured = { photoVm.sendPhoto(it) { treeVm.refresh() } },
                onWater = { treeVm.water(2) },
            )
            Spacer(Modifier.height(8.dp))
        }

        // Incoming messages from Norman pop up in the centre of the screen.
        IncomingMessageBanner(
            peerName = "Norman",
            photoVm = photoVm,
            voiceVm = voiceVm,
            modifier = Modifier.matchParentSize(),
        )
    }

    // Settings dialog (Sadie is user id 2). The Gallery opens from the bottom nav.
    GroveModals(
        showSettings = showSettings,
        onCloseSettings = { showSettings = false },
        threadsVm = threadsVm,
        onSwitchRole = onSwitchRole,
    )

    // "Wants to talk" nudge — a gentle wellbeing prompt, not an alarm. Dismissed
    // only by acknowledging so Sadie notices it.
    if (viewModel.state.emergency) {
        AlertDialog(
            onDismissRequest = { /* require an explicit acknowledgement */ },
            containerColor = Grove.Surface,
            icon = { Text(text = "💛", fontSize = 40.sp) },
            title = {
                Text(
                    text = "Norman would love to talk",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    color = Grove.Ink,
                    fontSize = 21.sp,
                )
            },
            text = {
                Text(
                    text = "He's feeling a little lonely and would love to hear from you. Give him a call or send a moment when you can.",
                    fontFamily = NunitoSans,
                    color = Grove.InkSoft,
                )
            },
            confirmButton = {
                Button(
                    onClick = onAllGood,
                    colors = ButtonDefaults.buttonColors(containerColor = Grove.Accent),
                ) {
                    Text(text = "I'll reach out", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}
