package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.viewmodels.AdultViewModel
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import com.drp33.quietsignal.viewmodels.PhotoMessagingViewModel
import com.drp33.quietsignal.viewmodels.TreeViewModel
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel

@Composable
fun AdultScreen(
    viewModel: AdultViewModel,
    treeVm: TreeViewModel,
    voiceVm: VoiceMessagingViewModel,
    photoVm: PhotoMessagingViewModel,
    memoriesVm: MemoriesViewModel,
    onSwitchRole: () -> Unit = {},
    onAllGood: () -> Unit = {},
) {
    val tree = treeVm.state
    var showMemories by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(text = "Your shared tree 🌳", fontSize = 22.sp, fontWeight = FontWeight.Bold)

            // Tap the tree to open the shared memory board.
            Box(modifier = Modifier.clickable { showMemories = true }) {
                WateringTree(stage = tree.stage, deathLevel = tree.deathLevel)
            }

            Text(
                text = "🌿 tap the tree to revisit your memories",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = treeHint(deathStateOf(tree.deathLevel)),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Three ways to nurture the shared tree — each also waters it.
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                VoiceRecorderButton(
                    onRecorded = { voiceVm.onRecorded(it, onUploaded = { treeVm.refresh() }) },
                    idleLabel = "Voice",
                    buttonSize = 96.dp,
                )
                WaterButton(onWater = { treeVm.water(2) }, size = 96.dp)
                SnapButton(onCaptured = { photoVm.sendPhoto(it) { treeVm.refresh() } }, size = 96.dp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Upload an existing photo from the device — shares it and adds it to the board.
            UploadButton(onSelected = { photoVm.sendPhoto(it) { treeVm.refresh() } }, size = 96.dp)

            Spacer(modifier = Modifier.height(20.dp))

            IncomingPhotoSection(peerName = "Norman", vm = photoVm)
            Spacer(modifier = Modifier.height(12.dp))
            IncomingVoiceSection(peerName = "Norman", vm = voiceVm)

            Spacer(modifier = Modifier.height(24.dp))
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

    if (showMemories) {
        MemoriesDialog(vm = memoriesVm, onClose = { showMemories = false })
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
