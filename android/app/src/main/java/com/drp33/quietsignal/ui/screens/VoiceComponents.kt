package com.drp33.quietsignal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.drp33.quietsignal.util.AudioPlayer
import com.drp33.quietsignal.util.AudioRecorder
import com.drp33.quietsignal.util.vibrateDoubleTap
import com.drp33.quietsignal.util.vibrateTick
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel
import kotlinx.coroutines.delay

/**
 * Tap-to-record mic button. The ring + scale react to the live microphone
 * amplitude, so it only moves while the speaker is actually talking.
 */
@Composable
fun VoiceRecorderButton(
    onRecorded: (ByteArray) -> Unit,
    idleLabel: String = "Tap to speak",
) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }

    var rawLevel by remember { mutableFloatStateOf(0f) }

    fun beginRecording() {
        if (recorder.start()) {
            isRecording = true
            context.vibrateTick()
        } else {
            Toast.makeText(context, "Couldn't access the microphone", Toast.LENGTH_SHORT).show()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginRecording()
        } else {
            Toast.makeText(context, "Microphone permission is needed to record", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            rawLevel = 0f
            return@LaunchedEffect
        }
        while (true) {
            val amp = recorder.amplitude() // 0..32767
            val normalized = (amp / 6000f).coerceIn(0f, 1f)
            rawLevel = if (normalized < 0.08f) 0f else normalized
            delay(60)
        }
    }

    val level by animateFloatAsState(
        targetValue = rawLevel,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "mic-level",
    )

    val containerColor by animateColorAsState(
        targetValue = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "mic-color",
    )
    val rippleColor = MaterialTheme.colorScheme.error

    Box(contentAlignment = Alignment.Center) {
        if (isRecording && level > 0f) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(1f + 0.9f * level)
                    .alpha(0.45f * level)
                    .background(rippleColor, CircleShape),
            )
        }

        Button(
            onClick = {
                if (isRecording) {
                    val bytes = recorder.stop()
                    isRecording = false
                    context.vibrateDoubleTap()
                    if (bytes != null) onRecorded(bytes)
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        beginRecording()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = containerColor),
            modifier = Modifier
                .size(120.dp)
                .scale(1f + 0.18f * level),
        ) {
            Text(text = "🎤", fontSize = 44.sp)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = if (isRecording) "Listening…" else idleLabel,
        style = MaterialTheme.typography.titleMedium,
    )
}

/**
 * Plays the peer's latest voice clip with a voice-message style scrubber.
 * Fetching always re-validates with the server, so an expired message can't be
 * played.
 */
@Composable
fun IncomingVoiceSection(
    peerName: String,
    vm: VoiceMessagingViewModel,
) {
    val context = LocalContext.current
    val player = remember { AudioPlayer(context) }

    var hasClip by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.position()
            delay(50)
        }
    }

    val state = vm.state

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
            },
        )
    } else {
        if (state.hasNewMessage) {
            Text(
                text = "🎙️ New voice message from $peerName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = {
                vm.playLatest { bytes ->
                    durationMs = player.play(bytes) {
                        // On finish, drop back to the button so replaying re-fetches
                        // and the server re-enforces the expiry window.
                        isPlaying = false
                        hasClip = false
                        positionMs = 0
                    }
                    positionMs = 0
                    isPlaying = true
                    hasClip = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "▶️ Play $peerName's message", fontSize = 20.sp)
        }

        if (state.status.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = state.status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun VoiceMessagePlayer(
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPlayPause,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp),
            ) {
                Text(text = if (isPlaying) "⏸" else "▶", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Slider(
                value = progress,
                onValueChange = onSeek,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = formatTime(if (positionMs > 0) positionMs else durationMs),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
