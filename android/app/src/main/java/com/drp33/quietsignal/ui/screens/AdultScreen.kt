package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.util.AudioPlayer
import com.drp33.quietsignal.viewmodels.AdultViewModel

// Norman is user 1; Sadie listens to his clips.
private const val NORMAN_ID = 1

@Composable
fun AdultScreen(viewModel: AdultViewModel) {
    val context = LocalContext.current
    val player = remember { AudioPlayer(context) }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    val state = viewModel.state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (state.checkedIn) "✅ Norman has checked in" else "Waiting for Norman…",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (state.hasNewVoice) {
            Text(
                text = "🎙️ New voice message from Norman",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = {
                viewModel.playLatestVoice(NORMAN_ID) { bytes -> player.play(bytes) }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "▶️ Play Norman's message",
                fontSize = 20.sp
            )
        }

        if (state.voiceStatus.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.voiceStatus,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
