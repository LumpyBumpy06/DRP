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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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
        memoriesVm.load(onlyMetadata = true)
        threadsVm.loadThreads()
    }
    val weekStart = (System.currentTimeMillis() / 1000 / WEEK_SECONDS) * WEEK_SECONDS
    val momentCount = memoriesVm.memories.count { (it.epoch / WEEK_SECONDS) * WEEK_SECONDS == weekStart }
    val lastMomentEpoch = memoriesVm.memories.maxOfOrNull { it.epoch }

    var showSettings by remember { mutableStateOf(false) }

    val promptsOn = remember(showSettings) { SettingsPreferences.promptsEnabled(context) }
    val prompt = threadsVm.prompt
    val showPrompt = promptsOn && mood == TreeMood.FADING && prompt != null
    // Window-y of the safety strip's bottom — the floating prompt hangs just below it.
    var promptAnchorPx by remember { mutableFloatStateOf(0f) }
    // A prompt thread is only created once the card is tapped AND a caption given.
    var promptClicked by remember { mutableStateOf(false) }
    var showPromptCaption by remember { mutableStateOf(false) }
    var promptImage by remember { mutableStateOf<ImageBitmap?>(null) }

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
                GroveCircleButton(glyph = "⚙️", contentDescription = "Settings", onClick = { showSettings = true })
                WantToTalkButton(onTrigger = onEmergencyClick)
            }

            GroveHeader(title = "Our garden", subtitle = "Little moments, shared with family.", momentCount = momentCount)

            Spacer(Modifier.height(12.dp))
            SafetyStrip(
                mood = mood,
                modifier = Modifier.onGloballyPositioned { promptAnchorPx = it.boundsInWindow().bottom },
                peerName = "Sadie",
                lastMomentEpoch = lastMomentEpoch
            )

            // Living tree — fills the middle. The gentle prompt is drawn OUTSIDE this
            // column as a floating overlay (below), so it never shifts the tree.
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                WateringTree(stage = tree.stage, deathLevel = tree.deathLevel)
            }

            Text(
                text = "Add a moment to help it grow",
                fontFamily = NunitoSans,
                fontSize = 13.5.sp,
                color = Grove.InkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            GroveInputRow(
                onVoiceRecorded = { voiceVm.onRecorded(it, onUploaded = { treeVm.refresh(); memoriesVm.load() }) },
                onPhotoCaptured = { photoVm.sendPhoto(it) { treeVm.refresh(); memoriesVm.load() } },
                onHello = { treeVm.revive(1) },
            )
            Spacer(Modifier.height(8.dp))
        }

        // The gentle "remember this?" prompt floats just under the safety strip,
        // fully outside the column so it never disturbs the tree's layout.
        // Preload the resurfaced photo so the card shows the picture itself
        // (not a placeholder) and the caption dialog opens with it ready.
        LaunchedEffect(prompt?.objectName) {
            prompt?.let { threadsVm.loadPromptImage(it.objectName) { img -> promptImage = img } }
        }

        // When the prompt card actually appears, nudge both partners once.
        LaunchedEffect(showPrompt, prompt?.threadAnchor) {
            if (showPrompt && prompt != null) threadsVm.announcePrompt(prompt.threadAnchor)
        }

        if (showPrompt && prompt != null) {
            PromptCard(
                title = "It's been quiet — remember this?",
                subtitle = "A ${if (prompt.type == "photo") "photo" else "voice note"} from ${prompt.sender}",
                image = promptImage,
                enlarged = promptClicked,
                onClick = {
                    promptClicked = true
                    showPromptCaption = true
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, promptAnchorPx.roundToInt() + 8.dp.roundToPx()) }
                    .padding(horizontal = 20.dp),
            )

            if (showPromptCaption) {
                PromptCaptionDialog(
                    image = promptImage,
                    onConfirm = { caption ->
                        threadsVm.openThread(prompt.threadAnchor, prompt.type, prompt.sender, title = caption)
                        // Starting the prompt waters the shared tree — refresh it.
                        treeVm.refresh()
                        showPromptCaption = false
                        promptClicked = false
                    },
                    onDismiss = { showPromptCaption = false; promptClicked = false },
                )
            }
        }

        IncomingMessageBanner(
            peerName = "Sadie",
            photoVm = photoVm,
            voiceVm = voiceVm,
            modifier = Modifier.matchParentSize(),
        )
    }

    // Settings dialog (Norman is user id 1). The Gallery opens from the bottom nav.
    GroveModals(
        showSettings = showSettings,
        onCloseSettings = { showSettings = false },
        threadsVm = threadsVm,
        onSwitchRole = onSwitchRole,
    )
}

/**
 * A warm "Want to talk" button. This is about wellbeing, not safety — Norman
 * taps it when he's feeling lonely and would love to hear from Sadie, and she
 * gets a gentle nudge to reach out. A soft buzz confirms, then it briefly locks
 * so a stray double-tap doesn't send twice.
 */
@Composable
private fun WantToTalkButton(onTrigger: () -> Unit, modifier: Modifier = Modifier) {
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
            containerColor = Grove.Accent,
            disabledContainerColor = Grove.Foliage,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
        modifier = modifier,
    ) {
        Text(
            text = if (sent) "💛 Sadie knows" else "💬 Want to talk",
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}
