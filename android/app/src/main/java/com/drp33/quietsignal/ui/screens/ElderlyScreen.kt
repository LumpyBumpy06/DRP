package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.model.ElderlyUIState
import com.drp33.quietsignal.viewmodels.TreeViewModel
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel

@Composable
fun ElderlyScreen(
    voiceVm: VoiceMessagingViewModel,
    treeVm: TreeViewModel,
    state: ElderlyUIState,
    name: String = "Norman",
    onOkayClick: () -> Unit = {},
    onNotTodayClick: () -> Unit = {},
    onReplyLaterClick: () -> Unit = {},
    onSwitchRole: () -> Unit = {},
    onCheckInRefresh: () -> Unit = {},
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

            TreeSection(state = treeVm.state, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(24.dp))

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
    }
}
