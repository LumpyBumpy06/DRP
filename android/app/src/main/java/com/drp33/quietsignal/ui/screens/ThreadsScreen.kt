package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.model.ThreadSummary
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.Newsreader
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.viewmodels.ThreadsViewModel

/**
 * The Threads tab — a WhatsApp-style list of every conversation, each hanging
 * off a photo or voice memo. Tapping a row opens [ThreadChatScreen] (hosted by
 * [MainShell]).
 */
@Composable
fun ThreadsPane(vm: ThreadsViewModel, contentPadding: PaddingValues = PaddingValues()) {
    LaunchedEffect(Unit) { vm.loadThreads() }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(contentPadding),
    ) {
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 12.dp)) {
            Text("Threads", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 28.sp, color = Grove.Ink)
            Spacer(Modifier.height(3.dp))
            Text("Conversations growing around your moments.", fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (vm.summaries.isEmpty()) {
                item { EmptyThreads() }
            }

            items(vm.summaries, key = { it.anchor }) { summary ->
                val title = vm.threadTitle(summary.anchor, summary.memorySender, summary.memoryType)
                ThreadRow(summary = summary, selfId = vm.selfId, unread = vm.unreadFor(summary), title = title, onClick = {
                    vm.openThread(summary.anchor, summary.memoryType, summary.memorySender)
                })
            }
        }
    }
}

@Composable
private fun ThreadRow(summary: ThreadSummary, selfId: Int, unread: Int, title: String, onClick: () -> Unit) {
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
        ) {
            if (summary.memoryType == "photo" && summary.image != null) {
                Image(
                    bitmap = summary.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(if (summary.memoryType == "photo") "📸" else "🎙", fontSize = 22.sp)
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = NunitoSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Grove.Ink, maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            val preview = if (summary.count == 0) {
                // A titled-but-empty thread: created from a caption, no messages yet.
                "Tap to start the conversation"
            } else {
                val who = if (summary.lastSenderId == selfId) "You: " else "${summary.lastSender}: "
                val body = when (summary.lastKind) {
                    "photo" -> "📷 Photo"
                    "voice" -> "🎙 Voice message"
                    else -> summary.lastText
                }
                "$who$body"
            }
            Text(preview, fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft, maxLines = 1)
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(threadTime(summary.lastEpoch), fontFamily = NunitoSans, fontSize = 11.5.sp, color = Grove.InkFaint)
            if (unread > 0) Badge(count = unread, color = Color(0xFFE0524B))
            else Spacer(Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Badge(count: Int, color: Color) {
    Box(
        modifier = Modifier.height(20.dp).widthIn(min = 20.dp).clip(CircleShape).background(color).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontSize = 11.5.sp,
            lineHeight = 11.5.sp,
            fontWeight = FontWeight.Bold,
            style = LocalTextStyle.current.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
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
