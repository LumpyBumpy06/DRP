package com.drp33.quietsignal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.drp33.quietsignal.model.ElderlyUIState
import com.drp33.quietsignal.util.AudioRecorder
import com.drp33.quietsignal.util.vibrateDoubleTap
import com.drp33.quietsignal.util.vibrateTick
import kotlinx.coroutines.delay

@Composable
fun ElderlyScreen(
    name: String = "Norman",
    onOkayClick: () -> Unit = {},
    onNotTodayClick: () -> Unit = {},
    onReplyLaterClick: () -> Unit = {},
    onVoiceRecorded: (ByteArray) -> Unit = {},
    state: ElderlyUIState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Hello, $name",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (state.isLoading){
            Text(
                text = "Loading..." ,
                style = MaterialTheme.typography.titleMedium
            )
        }
        else if (!state.showCheckIn){
            Text(
                text = "You have already checked in for today..." ,
                style = MaterialTheme.typography.titleMedium
            )
        }
        else{
            Text(
                text = "Just checking in! updated!",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "How are you feeling today?",
                fontSize = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            VoiceButton(onVoiceRecorded = onVoiceRecorded)

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onOkayClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "😊 I'm okay",
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNotTodayClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "😕 Not feeling great today",
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onReplyLaterClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⏰ I'll reply later",
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Composable
private fun VoiceButton(onVoiceRecorded: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }

    // Live mic level (0..1). Driven by the real microphone amplitude, so the
    // button only reacts when Norman is actually speaking — and how much it
    // reacts tracks how loud he is. Stays still during silence.
    var rawLevel by remember { mutableStateOf(0f) }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            rawLevel = 0f
            return@LaunchedEffect
        }
        while (true) {
            val amp = recorder.amplitude() // 0..32767
            val normalized = (amp / 6000f).coerceIn(0f, 1f)
            // Ignore background hiss so it doesn't twitch when the room is quiet.
            rawLevel = if (normalized < 0.08f) 0f else normalized
            delay(60)
        }
    }

    // Smooth the gaps between samples so movement looks fluid, not steppy.
    val level by animateFloatAsState(
        targetValue = rawLevel,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "mic-level"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "mic-color"
    )
    val rippleColor = MaterialTheme.colorScheme.error

    Box(contentAlignment = Alignment.Center) {

        // Ring grows/fades with his voice — invisible and still when silent.
        if (isRecording && level > 0f) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(1f + 0.9f * level)
                    .alpha(0.45f * level)
                    .background(rippleColor, CircleShape)
            )
        }

        Button(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return@Button

                if (isRecording) {
                    val bytes = recorder.stop()
                    isRecording = false
                    context.vibrateDoubleTap()
                    if (bytes != null) onVoiceRecorded(bytes)
                } else {
                    recorder.start()
                    isRecording = true
                    context.vibrateTick()
                }
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = containerColor),
            modifier = Modifier
                .size(120.dp)
                .scale(1f + 0.18f * level) // mic swells with how loud he speaks
        ) {
            Text(
                text = "🎤",
                fontSize = 44.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = if (isRecording) "Listening…" else "Tap to speak to Sadie",
        style = MaterialTheme.typography.titleMedium
    )
}
