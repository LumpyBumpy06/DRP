package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.util.AudioPlayer
import com.drp33.quietsignal.viewmodels.AdultViewModel
import kotlinx.coroutines.delay

// Norman is user 1; Sadie listens to his clips.
private const val NORMAN_ID = 1

@Composable
fun AdultScreen(viewModel: AdultViewModel) {
    val context = LocalContext.current
    val player = remember { AudioPlayer(context) }

    var hasClip by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // Advance the scrubber while the clip is playing.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.position()
            delay(50)
        }
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

        if (state.hasNewVoice && !hasClip) {
            Text(
                text = "🎙️ New voice message from Norman",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (hasClip) {
            VoiceMessagePlayer(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                onPlayPause = {
                    if (isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        if (durationMs > 0 && positionMs >= durationMs) {
                            player.seekTo(0)
                            positionMs = 0
                        }
                        player.resume()
                        isPlaying = true
                    }
                },
                onSeek = { fraction ->
                    val target = (fraction * durationMs).toInt()
                    positionMs = target
                    player.seekTo(target)
                }
            )
        } else {
            Button(
                onClick = {
                    viewModel.playLatestVoice(NORMAN_ID) { bytes ->
                        durationMs = player.play(bytes) {
                            // On finish, drop back to the button so replaying must
                            // re-fetch — the server then enforces the expiry window.
                            isPlaying = false
                            hasClip = false
                            positionMs = 0
                        }
                        positionMs = 0
                        isPlaying = true
                        hasClip = true
                    }
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
}

@Composable
private fun VoiceMessagePlayer(
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit
) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPlayPause,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Text(text = if (isPlaying) "⏸" else "▶", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Slider(
                value = progress,
                onValueChange = onSeek,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Counts up while playing; shows the clip length when idle.
            Text(
                text = formatTime(if (positionMs > 0) positionMs else durationMs),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
