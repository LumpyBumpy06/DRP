package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.model.MemoryItem
import com.drp33.quietsignal.ui.theme.GrovePalette
import com.drp33.quietsignal.viewmodels.MemoriesViewModel

private const val WEEK_SECONDS = 7L * 24 * 3600

/** One past week's worth of memories, with a stable per-week tree seed. */
data class GroveWeek(
    val seed: Int,
    val startEpoch: Long,
    val items: List<MemoryItem>,
)

/** Group memories (newest-first) into weeks by epoch, newest week first. */
fun groupIntoWeeks(memories: List<MemoryItem>): List<GroveWeek> =
    memories
        .groupBy { it.epoch / WEEK_SECONDS }
        .toSortedMap(compareByDescending { it })
        .map { (weekIndex, items) ->
            val start = weekIndex * WEEK_SECONDS
            // Hash the week start into a stable seed so both users see the same tree.
            val seed = (start xor (start ushr 32)).toInt() * 0x9E3779B1.toInt()
            GroveWeek(seed = seed, startEpoch = start, items = items)
        }

/**
 * A horizontally scrollable 2.5D forest of past weeks. Each week is a small
 * [GroveTree] on an alternating depth lane with parallax hills behind. Tap a tree
 * to open its [Montage].
 */
@Composable
fun ForestScreen(vm: MemoriesViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val weeks = remember(vm.memories) { groupIntoWeeks(vm.memories) }
    val scroll = rememberScrollState()
    var montageWeek by remember { mutableStateOf<GroveWeek?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(GrovePalette.bg)) {
        // Parallax hill layers — shift opposite the scroll at increasing rates.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val factors = listOf(0.15f to 0.78f, 0.32f to 0.86f, 0.55f to 0.94f)
            val tints = listOf(
                GrovePalette.foliage[2].copy(alpha = 0.30f),
                GrovePalette.foliage[0].copy(alpha = 0.40f),
                GrovePalette.foliage[1].copy(alpha = 0.55f),
            )
            factors.forEachIndexed { i, (factor, baseY) ->
                val shift = -scroll.value * factor
                val top = size.height * baseY
                val path = Path().apply {
                    moveTo(shift - size.width, size.height)
                    var x = shift - size.width
                    val step = size.width / 3f
                    lineTo(x, top)
                    while (x < shift + size.width * 3) {
                        val midX = x + step / 2f
                        val peak = top - size.height * 0.06f * ((i + 1))
                        quadraticBezierTo(midX, peak, x + step, top)
                        x += step
                    }
                    lineTo(x, size.height)
                    close()
                }
                drawPath(path, color = tints[i])
            }
        }

        if (weeks.isEmpty()) {
            Text(
                text = "🌱 Your forest is just beginning.\nShare moments this week to plant the first tree.",
                color = GrovePalette.inkSoft,
                modifier = Modifier.align(Alignment.Center).padding(40.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scroll)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                weeks.forEachIndexed { i, week ->
                    val near = i % 2 == 0
                    val treeHeight = if (near) 240.dp else 180.dp
                    val bottomPad = if (near) 60.dp else 140.dp
                    Column(
                        modifier = Modifier
                            .width(if (near) 200.dp else 170.dp)
                            .padding(bottom = bottomPad)
                            .graphicsLayer { alpha = if (near) 1f else 0.78f }
                            .clickable { montageWeek = week },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GroveTree(
                            stage = week.items.size.coerceIn(1, 5),
                            deathLevel = 0f,
                            blossomCount = week.items.size.coerceAtMost(20),
                            height = treeHeight,
                        )
                        Text(
                            text = "${week.items.size} ${if (week.items.size == 1) "moment" else "moments"}",
                            color = GrovePalette.inkSoft,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        // Header.
        Column(modifier = Modifier.statusBarsPadding().padding(start = 8.dp, top = 4.dp)) {
            TextButton(onClick = onBack) { Text("← This week", color = GrovePalette.ink) }
            Text(
                text = "Your forest · ${weeks.size} ${if (weeks.size == 1) "week" else "weeks"}",
                color = GrovePalette.ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }

    montageWeek?.let { week ->
        Montage(week = week, onClose = { montageWeek = null })
    }
}
