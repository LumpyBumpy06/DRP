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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.util.vibrateDoubleTap
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import com.drp33.quietsignal.viewmodels.PhotoMessagingViewModel
import com.drp33.quietsignal.viewmodels.ThreadsViewModel
import com.drp33.quietsignal.viewmodels.TreeViewModel
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel
import kotlinx.coroutines.delay

/**
 * Norman's home — the "This week" tab for the elder. Same Grove layout as
 * [AdultScreen] but the top row keeps the always-available SOS button, with the
 * Gallery + Settings buttons beside it.
 */
@Composable
fun ElderlyScreen(
    treeVm: TreeViewModel,
    voiceVm: VoiceMessagingViewModel,
    photoVm: PhotoMessagingViewModel,
    memoriesVm: MemoriesViewModel,
    threadsVm: ThreadsViewModel,
    name: String = "Norman",
    contentPadding: PaddingValues = PaddingValues(),
    onSwitchRole: () -> Unit = {},
    onEmergencyClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val tree = treeVm.state
    val mood = treeMoodOf(tree.deathLevel)

    LaunchedEffect(Unit) {
        memoriesVm.load()
        threadsVm.loadThreads()
    }
    val weekStart = (System.currentTimeMillis() / 1000 / WEEK_SECONDS) * WEEK_SECONDS
    val momentCount = memoriesVm.memories.count { (it.epoch / WEEK_SECONDS) * WEEK_SECONDS == weekStart }
    val lastMomentEpoch = memoriesVm.memories.maxOfOrNull { it.epoch }

    var showGallery by remember { mutableStateOf(false) }
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSwitchRole) {
                    Text("← Switch role", fontFamily = NunitoSans, color = Grove.InkSoft)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GroveHeaderActions(onGallery = { showGallery = true }, onSettings = { showSettings = true })
                    EmergencyButton(onTrigger = onEmergencyClick)
                }
            }

            GroveHeader(title = "Our garden", subtitle = "Little moments, shared with family.", momentCount = momentCount)

            Spacer(Modifier.height(12.dp))
            SafetyStrip(
                mood = mood,
                peerName = "Sadie",
                lastMomentEpoch = lastMomentEpoch
            )

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
                onWater = { treeVm.water(1) },
                onNote = { treeVm.water(1) },
            )
            Spacer(Modifier.height(8.dp))
        }

        IncomingMessageBanner(
            peerName = "Sadie",
            photoVm = photoVm,
            voiceVm = voiceVm,
            modifier = Modifier.matchParentSize(),
        )
    }

    // Gallery + Settings dialogs (Norman is user id 1).
    GroveModals(
        showGallery = showGallery,
        onCloseGallery = { showGallery = false },
        showSettings = showSettings,
        onCloseSettings = { showSettings = false },
        memoriesVm = memoriesVm,
        currentUserId = 1,
        threadsVm = threadsVm,
    )
}

/**
 * A small, unmistakably-red SOS button. Tapping it fires the alert immediately
 * (with a strong buzz), then briefly confirms and locks so a stray double-tap
 * can't spam Sadie.
 */
@Composable
private fun EmergencyButton(onTrigger: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var sent by remember { mutableStateOf(false) }

    LaunchedEffect(sent) {
        if (sent) {
            delay(4000)
            sent = false
        }
    }

    Button(
        onClick = {
            context.vibrateDoubleTap()
            onTrigger()
            sent = true
        },
        enabled = !sent,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFD32F2F),
            disabledContainerColor = Grove.Foliage,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = modifier,
    ) {
        Text(
            text = if (sent) "✓ Sadie alerted" else "🆘 Emergency",
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}
