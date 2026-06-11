package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.data.SettingsPreferences
import com.drp33.quietsignal.model.ThreadSummary
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.Newsreader
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.viewmodels.ThreadsViewModel

/**
 * The Threads tab — a WhatsApp-style list of every conversation, each hanging
 * off a photo or voice memo. A pinned "memory worth revisiting" prompt sits on
 * top when prompts are on. Tapping a row opens [ThreadChatScreen] (hosted by
 * [MainShell]).
 */
@Composable
fun ThreadsPane(vm: ThreadsViewModel, contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val promptsEnabled = remember { SettingsPreferences.promptsEnabled(context) }

    LaunchedEffect(Unit) { vm.loadThreads() }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(contentPadding),
    ) {
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 12.dp)) {
            Text("Threads", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 28.sp, color = Grove.Ink)
            Spacer(Modifier.height(3.dp))
            Text("Conversations growing around your moments.", fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft)
        }

        val prompt = vm.prompt
        val showPrompt = promptsEnabled && prompt != null

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (showPrompt) {
                item {
                    PromptThreadRow(
                        subtitle = "Grove · ${if (prompt!!.type == "photo") "📷 Photo" else "🎙 Voice note"} from ${prompt.sender}",
                        onClick = { vm.openThread(prompt.objectName, prompt.type, prompt.sender, isPrompt = true) },
                    )
                }
            }

            if (vm.summaries.isEmpty() && !showPrompt) {
                item { EmptyThreads() }
            }

            items(vm.summaries, key = { it.anchor }) { summary ->
                ThreadRow(summary = summary, selfId = vm.selfId, onClick = {
                    vm.openThread(summary.anchor, summary.memoryType, summary.memorySender)
                })
            }
        }
    }
}

@Composable
private fun PromptThreadRow(subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Grove.Accent.copy(alpha = 0.10f))
            .border(1.dp, Grove.Accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Grove.Accent), contentAlignment = Alignment.Center) {
            Text("✨", fontSize = 22.sp)
        }
        Column(Modifier.weight(1f)) {
            Text("A memory worth revisiting", fontFamily = NunitoSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Grove.Ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PROMPT", fontFamily = NunitoSans, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = Grove.Accent)
            Badge(count = 1, color = Grove.Accent)
        }
    }
}

@Composable
private fun ThreadRow(summary: ThreadSummary, selfId: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(Grove.Surface)
            .clickable { onClick() }
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp))
                .background(if (summary.memoryType == "photo") Grove.Photo.copy(alpha = 0.18f) else Grove.Voice.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Text(if (summary.memoryType == "photo") "📸" else "🎙", fontSize = 22.sp) }

        Column(Modifier.weight(1f)) {
            Text(
                text = "${summary.memorySender}'s ${if (summary.memoryType == "photo") "photo" else "voice note"}",
                fontFamily = NunitoSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Grove.Ink, maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            val who = if (summary.lastSenderId == selfId) "You: " else "${summary.lastSender}: "
            val preview = when (summary.lastKind) {
                "photo" -> "📷 Photo"
                "voice" -> "🎙 Voice message"
                else -> summary.lastText
            }
            Text("$who$preview", fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft, maxLines = 1)
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(threadTime(summary.lastEpoch), fontFamily = NunitoSans, fontSize = 11.5.sp, color = Grove.InkFaint)
            if (summary.incoming > 0) Badge(count = summary.incoming, color = Color(0xFFE0524B))
            else Spacer(Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Badge(count: Int, color: Color) {
    Box(
        modifier = Modifier.height(20.dp).clip(CircleShape).background(color).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = count.toString(), color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyThreads() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp, start = 30.dp, end = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("💬", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No threads yet.\nOpen a moment in the Gallery to start a conversation.",
            fontFamily = NunitoSans, fontSize = 14.5.sp, color = Grove.InkSoft, textAlign = TextAlign.Center, lineHeight = 20.sp,
        )
    }
}
