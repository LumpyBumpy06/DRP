package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.viewmodels.AdultViewModel
import com.drp33.quietsignal.viewmodels.TreeViewModel
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel

@Composable
fun AdultScreen(
    viewModel: AdultViewModel,
    treeVm: TreeViewModel,
    voiceVm: VoiceMessagingViewModel,
    onSwitchRole: () -> Unit = {},
    onAllGood: () -> Unit = {},
) {
    val tree = treeVm.state

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(text = "Your shared tree 🌳", fontSize = 22.sp, fontWeight = FontWeight.Bold)

            WateringTree(stage = tree.stage, deathLevel = tree.deathLevel)

            Text(
                text = treeHint(deathStateOf(tree.deathLevel)),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Mic (send a memo — also waters) on the left, water on the right.
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top,
            ) {
                VoiceRecorderButton(
                    onRecorded = { voiceVm.onRecorded(it, onUploaded = { treeVm.refresh() }) },
                    idleLabel = "Send a memo",
                )
                WaterButton(onWater = { treeVm.water(2) })
            }

            Spacer(modifier = Modifier.height(20.dp))

            IncomingVoiceSection(peerName = "Norman", vm = voiceVm)
        }

        TextButton(
            onClick = onSwitchRole,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Text("← Switch role")
        }
    }

    // Emergency popup — only dismissible via "All good" so it must be acknowledged.
    if (viewModel.state.emergency) {
        AlertDialog(
            onDismissRequest = { /* require an explicit acknowledgement */ },
            icon = { Text(text = "🚨", fontSize = 40.sp) },
            title = {
                Text(
                    text = "Emergency",
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = { Text(text = "Norman pressed the emergency button. Please check on him right away.") },
            confirmButton = {
                Button(
                    onClick = onAllGood,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                ) {
                    Text(text = "All good")
                }
            },
        )
    }
}
