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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drp33.quietsignal.viewmodels.AdultViewModel
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel

@Composable
fun AdultScreen(
    viewModel: AdultViewModel,
    voiceVm: VoiceMessagingViewModel,
    onSwitchRole: () -> Unit = {},
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
}
