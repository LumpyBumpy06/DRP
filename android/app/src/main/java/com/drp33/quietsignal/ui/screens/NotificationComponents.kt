package com.drp33.quietsignal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.drp33.quietsignal.util.AudioPlayer
import com.drp33.quietsignal.viewmodels.PhotoMessagingViewModel
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel
import kotlinx.coroutines.delay

/**
 * A WhatsApp-style heads-up notification stack pinned to the top of the screen.
 * When the peer sends a snap or a voice clip, a banner slides down from the top;
 * tapping it opens the content right there. Replaces the old inline cards that
 * pushed the rest of the layout around.
 */
@Composable
fun IncomingMessageBanner(
    peerName: String,
    photoVm: PhotoMessagingViewModel,
    voiceVm: VoiceMessagingViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PhotoBanner(peerName = peerName, vm = photoVm)
        VoiceBanner(peerName = peerName, vm = voiceVm)
    }
}

@Composable
private fun PhotoBanner(peerName: String, vm: PhotoMessagingViewModel) {
    val image = vm.state.image
    val visible = image != null && vm.state.isNew
    var expanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    ) {
        HeadsUpCard(
            leading = {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
            },
            title = "📸 $peerName sent a snap",
            subtitle = "Tap to view",
            onClick = { expanded = true },
            onDismiss = { vm.markSeen() },
        )
    }

    if (expanded && image != null) {
        Dialog(onDismissRequest = { expanded = false; vm.markSeen() }) {
            Image(
                bitmap = image,
                contentDescription = "Snap from $peerName",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { expanded = false; vm.markSeen() },
            )
        }
    }
}

@Composable
private fun VoiceBanner(peerName: String, vm: VoiceMessagingViewModel) {
    val context = LocalContext.current
    val player = remember { AudioPlayer(context) }

    var open by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.position()
            delay(50)
        }
    }

    AnimatedVisibility(
        visible = vm.state.hasNewMessage,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    ) {
        HeadsUpCard(
            leading = { Text(text = "🎙️", fontSize = 28.sp) },
            title = "$peerName sent a voice message",
            subtitle = "Tap to play",
            onClick = {
                open = true
                vm.playLatest { bytes ->
                    durationMs = player.play(bytes) {
                        isPlaying = false
                        positionMs = 0
                    }
                    positionMs = 0
                    isPlaying = true
                }
            },
            onDismiss = { vm.playLatest { } },
        )
    }

    if (open) {
        Dialog(onDismissRequest = {
            player.pause()
            isPlaying = false
            open = false
        }) {
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
        }
    }
}

/** The shared heads-up card: an icon/thumbnail, title + subtitle, and a dismiss ✕. */
@Composable
private fun HeadsUpCard(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDismiss) { Text("✕", color = Color.Gray) }
        }
    }
}
