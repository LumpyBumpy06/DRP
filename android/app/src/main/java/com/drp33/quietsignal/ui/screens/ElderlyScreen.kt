package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import com.drp33.quietsignal.util.vibrateDoubleTap
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import com.drp33.quietsignal.viewmodels.PhotoMessagingViewModel
import com.drp33.quietsignal.viewmodels.TreeViewModel
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel
import kotlinx.coroutines.delay

@Composable
fun ElderlyScreen(
    treeVm: TreeViewModel,
    voiceVm: VoiceMessagingViewModel,
    photoVm: PhotoMessagingViewModel,
    memoriesVm: MemoriesViewModel,
    name: String = "Norman",
    onSwitchRole: () -> Unit = {},
    onEmergencyClick: () -> Unit = {},
    onOpenForest: () -> Unit = {},
) {
    val tree = treeVm.state

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar: Switch role on the left, the always-available SOS on the
            // right. Keeping them in a dedicated row stops the Emergency button
            // from overlapping the title.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSwitchRole) {
                    Text("← Switch role")
                }
                EmergencyButton(onTrigger = onEmergencyClick)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(text = "$name's tree 🌳", fontSize = 22.sp, fontWeight = FontWeight.Bold)

            WateringTree(stage = tree.stage, deathLevel = tree.deathLevel)

            Text(
                text = treeHint(treeMoodOf(tree.deathLevel)),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Each week's tree joins the shared forest — tap to look back on them.
            Button(
                onClick = onOpenForest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            ) {
                Text(text = "🌲 Visit your forest", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

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
                WaterButton(onWater = { treeVm.water(1) }, size = 96.dp)
                SnapButton(onCaptured = { photoVm.sendPhoto(it) { treeVm.refresh() } }, size = 96.dp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Incoming messages from Sadie pop up in the centre of the screen.
        IncomingMessageBanner(
            peerName = "Sadie",
            photoVm = photoVm,
            voiceVm = voiceVm,
            modifier = Modifier.matchParentSize(),
        )
    }

}

/**
 * A small, unmistakably-red SOS button. Tapping it fires the alert immediately
 * (with a strong buzz), then briefly confirms and locks so a stray double-tap
 * can't spam Sadie.
 */
@Composable
private fun EmergencyButton(
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            disabledContainerColor = Color(0xFF2E7D32),
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
