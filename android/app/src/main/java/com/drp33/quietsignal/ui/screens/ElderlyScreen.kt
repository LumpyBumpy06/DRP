package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.model.ElderlyUIState
import com.drp33.quietsignal.util.vibrateDoubleTap
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel
import kotlinx.coroutines.delay

@Composable
fun ElderlyScreen(
    voiceVm: VoiceMessagingViewModel,
    state: ElderlyUIState,
    name: String = "Norman",
    onOkayClick: () -> Unit = {},
    onNotTodayClick: () -> Unit = {},
    onReplyLaterClick: () -> Unit = {},
    onSwitchRole: () -> Unit = {},
    onCheckInRefresh: () -> Unit = {},
    onEmergencyClick: () -> Unit = {},
) {
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
                text = "Hello, $name",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                Text(text = "Loading...", style = MaterialTheme.typography.titleMedium)
            } else if (!state.showCheckIn) {
                Text(
                    text = "You have already checked in for today...",
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Text(
                    text = "Just checking in! updated!",
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "How are you feeling today?", fontSize = 22.sp)

                Spacer(modifier = Modifier.height(24.dp))

                // Norman's recording doubles as his "I'm okay" check-in for Sadie.
                VoiceRecorderButton(
                    onRecorded = { voiceVm.onRecorded(it, onUploaded = onCheckInRefresh) },
                    idleLabel = "Tap to speak to Sadie",
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(onClick = onOkayClick, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "😊 I'm okay", fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = onNotTodayClick, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "😕 Not feeling great today", fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(onClick = onReplyLaterClick, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "⏰ I'll reply later", fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Messages from Sadie — available whatever the check-in status.
            Text(text = "Message from Sadie", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            IncomingVoiceSection(peerName = "Sadie", vm = voiceVm)
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

        // Always-available SOS in the opposite corner — one tap alerts Sadie.
        EmergencyButton(
            onTrigger = onEmergencyClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
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
