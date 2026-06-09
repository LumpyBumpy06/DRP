package com.drp33.quietsignal.ui.screens

import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.ui.theme.GrovePalette

private const val SLIDE_MS = 3000f

/** True when the system "remove animations" / reduce-motion setting is on. */
private fun reduceMotionEnabled(scale: Float?): Boolean = scale != null && scale == 0f

/**
 * A stories-style recap of one [GroveWeek]: a title slide, one slide per memory,
 * and a closing slide. Auto-advances (~3s), tap left/right to navigate, hold to
 * pause, X to close. Honors the system reduce-motion setting (no auto-advance).
 */
@Composable
fun Montage(week: GroveWeek, onClose: () -> Unit) {
    val context = LocalContext.current
    val animScale = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        }.getOrNull()
    }
    val reduceMotion = reduceMotionEnabled(animScale)

    val total = week.items.size + 2
    var current by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }

    fun next() { if (current < total - 1) { current++ } else onClose() }
    fun prev() { if (current > 0) current-- }

    // Auto-advance timer (skipped under reduce-motion).
    LaunchedEffect(current, reduceMotion) {
        progress = 0f
        if (reduceMotion) return@LaunchedEffect
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
            .background(GrovePalette.bg)
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
                0 -> TitleSlide(week)
                total - 1 -> EndSlide()
                else -> MemorySlide(week, idx - 1)
            }
        }

        // Top progress bars.
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
                    i == current -> if (reduceMotion) 0f else progress
                    else -> 0f
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(GrovePalette.inkSoft.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fill)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(GrovePalette.accent),
                    )
                }
            }
        }

        Text(
            text = "✕",
            color = GrovePalette.ink,
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
private fun TitleSlide(week: GroveWeek) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GroveTree(stage = 5, deathLevel = 0f, blossomCount = week.items.size.coerceAtMost(20), height = 260.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Your week together",
            color = GrovePalette.ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${week.items.size} ${if (week.items.size == 1) "moment" else "moments"}",
            color = GrovePalette.inkSoft,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun MemorySlide(week: GroveWeek, memoryIndex: Int) {
    val item = week.items[memoryIndex]
    val accent = GrovePalette.accentFor(item.type)
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
            if (item.type == "photo" && image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "Photo from ${item.sender}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                )
            } else if (item.type == "voice") {
                Waveform(color = accent, modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 40.dp))
            } else {
                Text(text = GrovePalette.glyphFor(item.type), fontSize = 80.sp)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "${item.sender} ${GrovePalette.verbFor(item.type)}",
            color = GrovePalette.ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(text = groveRelativeTime(item.epoch), color = GrovePalette.inkSoft, fontSize = 14.sp)
    }
}

@Composable
private fun EndSlide() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GrovePalette.bg, GrovePalette.foliage[2].copy(alpha = 0.25f)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(80.dp)) {
                drawCircle(GrovePalette.foliage[0], radius = size.minDimension / 3f, center = Offset(size.width / 2f, size.height / 2f))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "This tree is planted", color = GrovePalette.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}
