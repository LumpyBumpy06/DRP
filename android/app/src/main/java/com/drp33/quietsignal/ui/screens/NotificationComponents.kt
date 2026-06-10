package com.drp33.quietsignal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.drp33.quietsignal.util.AudioPlayer
import com.drp33.quietsignal.viewmodels.PhotoMessagingViewModel
import com.drp33.quietsignal.viewmodels.VoiceMessagingViewModel
import kotlinx.coroutines.delay

/**
 * A big, friendly notification that appears in the CENTRE of the screen when the
 * peer sends a snap or a voice clip — much easier for older eyes than a small
 * banner tucked up at the top. The screen behind dims, and a large card invites
 * them to listen/view with one clear button. When two messages arrive back to
 * back, the card shows the unread count ("2 new voice messages").
 */
@Composable
fun IncomingMessageBanner(
    peerName: String,
    photoVm: PhotoMessagingViewModel,
    voiceVm: VoiceMessagingViewModel,
    modifier: Modifier = Modifier,
) {
    val photoVisible = photoVm.state.image != null && photoVm.state.isNew
    val voiceVisible = voiceVm.state.hasNewMessage
    val anyVisible = photoVisible || voiceVisible

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Dim the screen behind so the popup is unmistakably the focus.
        AnimatedVisibility(
            visible = anyVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PhotoPopup(peerName = peerName, vm = photoVm, visible = photoVisible)
            VoicePopup(peerName = peerName, vm = voiceVm, visible = voiceVisible)
        }
    }
}

@Composable
private fun PhotoPopup(peerName: String, vm: PhotoMessagingViewModel, visible: Boolean) {
    val image = vm.state.image
    var expanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(tween(220), initialScale = 0.85f) + fadeIn(),
        exit = scaleOut(tween(180), targetScale = 0.85f) + fadeOut(),
    ) {
        CenterPopupCard(
            emoji = "📸",
            title = unreadTitle(vm.state.unreadCount, peerName, "photo", "photos", fallback = "$peerName sent a photo"),
            subtitle = "Tap the button to see it",
            primaryLabel = "View photo",
            onPrimary = { expanded = true },
            onDismiss = { vm.markSeen() },
            leading = {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
            },
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
private fun VoicePopup(peerName: String, vm: VoiceMessagingViewModel, visible: Boolean) {
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
        visible = visible,
        enter = scaleIn(tween(220), initialScale = 0.85f) + fadeIn(),
        exit = scaleOut(tween(180), targetScale = 0.85f) + fadeOut(),
    ) {
        CenterPopupCard(
            emoji = "🎙️",
            title = unreadTitle(vm.state.unreadCount, peerName, "voice message", "voice messages", fallback = "$peerName sent a voice message"),
            subtitle = "Tap the button to listen",
            primaryLabel = "▶  Listen",
            onPrimary = {
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
            leading = { Text(text = "🎙️", fontSize = 44.sp) },
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

/** "Sadie sent you 2 voice messages" when more than one is waiting, else [fallback]. */
private fun unreadTitle(count: Int, peerName: String, singular: String, plural: String, fallback: String): String =
    if (count > 1) "$peerName sent you $count $plural" else fallback

/**
 * The shared centred popup: a large emoji/thumbnail, a big headline, a one-line
 * hint, a single prominent action button, and a quiet "Later" to dismiss.
 * Sized and spaced generously so it's easy to read and tap.
 */
@Composable
private fun CenterPopupCard(
    emoji: String,
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        // Swallow taps on the card so they don't fall through to the scrim.
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            leading()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onPrimary,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            ) {
                Text(text = primaryLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onDismiss) {
                Text(text = "Later", fontSize = 17.sp, color = Color.Gray)
            }
        }
    }
}
