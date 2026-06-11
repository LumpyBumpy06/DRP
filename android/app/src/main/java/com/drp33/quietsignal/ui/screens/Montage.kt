package com.drp33.quietsignal.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.model.ForestWeek
import com.drp33.quietsignal.model.MemoryItem
import com.drp33.quietsignal.util.AudioPlayer
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import kotlin.math.abs
import kotlin.math.sin

private const val SLIDE_MS = 3500f
private val ACCENT = Color(0xFF2E7D32)

/** Accent colour per memory type. */
private fun accentFor(type: String): Color = when (type) {
    "photo" -> Color(0xFFC26B4E)
    "voice" -> Color(0xFF5E93AE)
    else -> ACCENT
}

private fun glyphFor(type: String): String = when (type) {
    "photo" -> "📸"
    "voice" -> "🎤"
    else -> "🌱"
}

private fun verbFor(type: String): String = when (type) {
    "photo" -> "shared a photo"
    "voice" -> "left a voice note"
    else -> "added a moment"
}

/**
 * A stories-style recap of one week: a title slide with that week's frozen tree,
 * one slide per memory, then a closing slide. Auto-advances (~3.5s); tap the left
 * half to go back, the right half forward; press-and-hold to pause; ✕ to close.
 */
@Composable
fun Montage(week: ForestWeek, memories: List<MemoryItem>, vm: MemoriesViewModel, onClose: () -> Unit) {
    val total = memories.size + 2
    var current by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }

    fun next() { if (current < total - 1) current++ else onClose() }
    fun prev() { if (current > 0) current-- }

    // Auto-advance the current slide.
    LaunchedEffect(current) {
        progress = 0f
        var last = 0L
        while (progress < 1f) {
            val t = withFrameMillis { it }
            if (last != 0L && !paused) progress += (t - last) / SLIDE_MS
            last = t
        }
        next()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(total) {
                detectTapGestures(
                    onPress = {
                        paused = true
                        tryAwaitRelease()
                        paused = false
                    },
                    onTap = { o -> if (o.x < size.width / 2f) prev() else next() },
                )
            },
    ) {
        AnimatedContent(
            targetState = current,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "montage-slide",
        ) { idx ->
            when (idx) {
                0 -> TitleSlide(week, memories.size)
                total - 1 -> EndSlide()
                else -> MemorySlide(memories[idx - 1], vm)
            }
        }

        // Progress bars across the top.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (i in 0 until total) {
                val fill = when {
                    i < current -> 1f
                    i == current -> progress
                    else -> 0f
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fill)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ACCENT),
                    )
                }
            }
        }

        Text(
            text = "✕",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 20.dp, end = 16.dp)
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
    }
}

@Composable
private fun TitleSlide(week: ForestWeek, count: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WateringTree(stage = week.stage, deathLevel = week.deathLevel)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your week together",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "$count ${if (count == 1) "moment" else "moments"}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun MemorySlide(item: MemoryItem, vm: MemoriesViewModel) {
    val accent = accentFor(item.type)
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            val image = item.image
            when {
                item.type == "photo" && image != null -> Image(
                    bitmap = image,
                    contentDescription = "Photo from ${item.sender}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                )
                item.type == "voice" -> VoiceSlidePlayer(item, vm, accent)
                else -> Text(text = glyphFor(item.type), fontSize = 80.sp)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "${item.sender} ${verbFor(item.type)}",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

/** A tappable play control for a voice memo inside its montage slide. */
@Composable
private fun VoiceSlidePlayer(item: MemoryItem, vm: MemoriesViewModel, accent: Color) {
    val context = LocalContext.current
    val player = remember { AudioPlayer(context) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(item.objectName) { onDispose { player.release() } }

    Row(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(accent)
                .pointerInput(item.objectName) {
                    detectTapGestures {
                        if (playing) {
                            player.pause()
                            playing = false
                        } else {
                            vm.loadMediaBytes(item.objectName) { bytes ->
                                player.play(bytes) { playing = false }
                                playing = true
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = if (playing) "⏸" else "▶", color = Color.White, fontSize = 26.sp)
        }
        Waveform(color = accent, modifier = Modifier.weight(1f).height(48.dp))
    }
}

/** A simple static waveform — deterministic so it looks the same each open. */
@Composable
private fun Waveform(color: Color, modifier: Modifier = Modifier, bars: Int = 28) {
    Canvas(modifier = modifier) {
        val gap = size.width / bars
        val barW = gap * 0.5f
        for (i in 0 until bars) {
            val h = (0.35f + 0.65f * abs(sin(i * 1.7f) * 0.6f + sin(i * 0.6f) * 0.4f)) * size.height
            val x = i * gap + gap * 0.25f
            val top = (size.height - h) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, top),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

@Composable
private fun EndSlide() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, ACCENT.copy(alpha = 0.18f)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🌳", fontSize = 72.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "This week is planted",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
