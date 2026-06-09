package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.model.MemoryItem
import com.drp33.quietsignal.ui.theme.GrovePalette
import com.drp33.quietsignal.util.AudioPlayer
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import kotlin.math.abs
import kotlin.math.sin

/**
 * A calm bottom sheet for one memory: who added it, when, and the content itself
 * (photo, a playable voice note, a water heart, or a note). Reuses [AudioPlayer]
 * and [MemoriesViewModel]'s media plumbing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDetailSheet(item: MemoryItem, vm: MemoriesViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val player = remember { AudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var playing by remember { mutableStateOf(false) }
    val accent = GrovePalette.accentFor(item.type)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GrovePalette.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Header: type chip + "<sender> <verb>" + time.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = GrovePalette.glyphFor(item.type), fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column {
                    Text(
                        text = "${item.sender} ${GrovePalette.verbFor(item.type)}",
                        color = GrovePalette.ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = groveRelativeTime(item.epoch),
                        color = GrovePalette.inkSoft,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (item.type) {
                "photo" -> {
                    val image = item.image
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = "Photo from ${item.sender}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .clip(RoundedCornerShape(20.dp)),
                        )
                    } else {
                        PlaceholderBox(accent, "📷")
                    }
                }

                "water" -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "💛", fontSize = 64.sp)
                }

                else -> { // voice (and any unknown type) — show a playable waveform card.
                    VoicePlayerCard(
                        accent = accent,
                        playing = playing,
                        onToggle = {
                            if (playing) {
                                player.pause()
                                playing = false
                            } else {
                                vm.loadMediaBytes(item.objectName) { bytes ->
                                    player.play(bytes) { playing = false }
                                    playing = true
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderBox(accent: Color, glyph: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, fontSize = 44.sp)
    }
}

@Composable
private fun VoicePlayerCard(accent: Color, playing: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = if (playing) "⏸" else "▶", color = Color.White, fontSize = 22.sp)
        }
        Waveform(color = accent, modifier = Modifier.weight(1f).height(40.dp))
    }
}

/** A small static waveform — deterministic so it looks the same each open. */
@Composable
fun Waveform(color: Color, modifier: Modifier = Modifier, bars: Int = 28) {
    Canvas(modifier = modifier) {
        val gap = size.width / bars
        val barW = gap * 0.5f
        for (i in 0 until bars) {
            // A gentle pseudo-random envelope from a sine mix — stable per index.
            val h = (0.35f + 0.65f * abs(sin(i * 1.7f) * 0.6f + sin(i * 0.6f) * 0.4f)) * size.height
            val x = i * gap + gap * 0.25f
            val top = (size.height - h) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

/** Relative "3h ago" style timestamp from a unix-second epoch. */
fun groveRelativeTime(epochSec: Long): String {
    val diff = System.currentTimeMillis() / 1000 - epochSec
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        else -> "${diff / 86400}d ago"
    }
}
