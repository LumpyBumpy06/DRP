package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.viewmodels.AdultViewModel
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel

@Composable
fun AdultScreen(
    viewModel: AdultViewModel,
    voiceVm: VoiceMessagingViewModel,
    onSwitchRole: () -> Unit = {},
    onAllGood: () -> Unit = {},
) {
    val state = viewModel.state

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (state.checkedIn) "✅ Norman has checked in" else "Waiting for Norman…",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Listen to Norman's latest message.
            Text(text = "Message from Norman", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            IncomingVoiceSection(peerName = "Norman", vm = voiceVm)

            Spacer(modifier = Modifier.height(32.dp))

            // Record a message back to Norman.
            Text(text = "Send a message to Norman", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            VoiceRecorderButton(
                onRecorded = { voiceVm.onRecorded(it) },
                idleLabel = "Tap to record for Norman",
            )
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
    if (state.emergency) {
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
