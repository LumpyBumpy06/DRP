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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.model.WEEK_SECONDS
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.viewmodels.AdultViewModel
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import com.drp33.quietsignal.viewmodels.PhotoMessagingViewModel
import com.drp33.quietsignal.viewmodels.TreeViewModel
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel

/**
 * Sadie's home — the "This week" tab. Grove layout: header + quiet-safety strip,
 * the living [WateringTree] (kept exactly as before), a prompt line, and the
 * Voice · Photo · Hello · Note input row. The forest now lives in the bottom
 * tab bar provided by [MainShell].
 */
@Composable
fun AdultScreen(
    viewModel: AdultViewModel,
    treeVm: TreeViewModel,
    voiceVm: VoiceMessagingViewModel,
    photoVm: PhotoMessagingViewModel,
    memoriesVm: MemoriesViewModel,
    contentPadding: PaddingValues = PaddingValues(),
    onSwitchRole: () -> Unit = {},
    onAllGood: () -> Unit = {},
) {
    val tree = treeVm.state
    val mood = treeMoodOf(tree.deathLevel)

    LaunchedEffect(Unit) { memoriesVm.load() }
    val weekStart = (System.currentTimeMillis() / 1000 / WEEK_SECONDS) * WEEK_SECONDS
    val momentCount = memoriesVm.memories.count { (it.epoch / WEEK_SECONDS) * WEEK_SECONDS == weekStart }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                TextButton(onClick = onSwitchRole) {
                    Text("← Switch role", fontFamily = NunitoSans, color = Grove.InkSoft)
                }
            }

            GroveHeader(title = "Mum's grove", subtitle = "A living record of staying close.", momentCount = momentCount)

            Spacer(Modifier.height(12.dp))
            SafetyStrip(mood = mood)

            // Living tree — fills the middle. Untouched WateringTree (tree.json).
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
                onVoiceRecorded = { voiceVm.onRecorded(it, onUploaded = { treeVm.refresh() }) },
                onPhotoCaptured = { photoVm.sendPhoto(it) { treeVm.refresh() } },
                onWater = { treeVm.water(2) },
                // No note backend yet — a note still nurtures the shared tree.
                onNote = { treeVm.water(1) },
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

    // Emergency popup — only dismissible via "All good" so it must be acknowledged.
    if (viewModel.state.emergency) {
        AlertDialog(
            onDismissRequest = { /* require an explicit acknowledgement */ },
            containerColor = Grove.Surface,
            icon = { Text(text = "🚨", fontSize = 40.sp) },
            title = { Text(text = "Emergency", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold) },
            text = { Text(text = "Norman pressed the emergency button. Please check on him right away.", fontFamily = NunitoSans) },
            confirmButton = {
                Button(
                    onClick = onAllGood,
                    colors = ButtonDefaults.buttonColors(containerColor = Grove.Foliage),
                ) {
                    Text(text = "All good", color = Color.White)
                }
            },
        )
    }
}
