package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.model.MemoryItem
import com.drp33.quietsignal.ui.theme.GrovePalette
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import kotlin.math.abs

// ---- Roots layout constants (authored in dp; the canvas converts to px) ------
private val TREE_HEIGHT = 500.dp     // sky + full canopy + trunk in the first viewport
private val SOIL_Y = 432.dp          // soil line; trunk base sits just above it
private val FIRST_MEMORY_Y = 560.dp  // first rootlet attach depth
private val MEMORY_GAP = 168.dp      // vertical gap between memories
private val CARD_WIDTH = 156.dp
private val CARD_OFFSET = 116.dp     // card centre distance from the trunk axis
private val FOOTER = 220.dp

/** Tiny deterministic RNG so the root tangle is identical for both users. */
private fun seededRandom(seed: Int): () -> Float {
    var s = seed
    return {
        s = (s * 1103515245 + 12345) and 0x7FFFFFFF
        s / 0x7FFFFFFF.toFloat()
    }
}

/**
 * The "follow the roots" descent: the shared tree at the top, then a scroll down
 * into the soil where every memory hangs off the taproot as a card. Tapping a
 * card opens the [MemoryDetailSheet].
 */
@Composable
fun RootsScreen(vm: MemoriesViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val memories = vm.memories
    val scroll = rememberScrollState()
    var selected by remember { mutableStateOf<MemoryItem?>(null) }

    val totalHeight = FIRST_MEMORY_Y + MEMORY_GAP * memories.size.coerceAtLeast(1) + FOOTER

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(GrovePalette.bg)) {
        val cx = maxWidth / 2

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
                // Background: sky, soil, taproot, rootlets, hair roots, nodes.
                RootsCanvas(memoryCount = memories.size, cx = cx)

                // The living tree, bottom-anchored so its trunk meets the soil line.
                GroveTree(
                    stage = 5,
                    deathLevel = 0f,
                    blossomCount = memories.size.coerceAtMost(20),
                    height = TREE_HEIGHT,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                // Fading hint near the soil line.
                if (scroll.value < 200) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = SOIL_Y - 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "follow the roots ↓",
                            color = GrovePalette.ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = "each one holds a memory",
                            color = GrovePalette.inkSoft,
                            fontSize = 13.sp,
                        )
                    }
                }

                // Memory cards, alternating sides down the taproot.
                memories.forEachIndexed { i, item ->
                    val side = if (i % 2 == 0) -1 else 1
                    val attachY = FIRST_MEMORY_Y + MEMORY_GAP * i
                    val cardLeft = cx + (CARD_OFFSET * side) - CARD_WIDTH / 2
                    Box(
                        modifier = Modifier.offset(x = cardLeft, y = attachY - 52.dp),
                    ) {
                        RootMemoryCard(item = item, onClick = { selected = item })
                    }
                }

                // End cap.
                Text(
                    text = "the foundation of this week",
                    color = GrovePalette.inkSoft,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = totalHeight - FOOTER + 64.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                )
            }
        }

        // Depth gauge on the right edge.
        val progress = if (scroll.maxValue == 0) 0f else scroll.value.toFloat() / scroll.maxValue
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .width(4.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GrovePalette.inkSoft.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .offset(y = (120.dp - 28.dp) * progress)
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GrovePalette.accent),
            )
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
        ) {
            Text("← Back", color = GrovePalette.ink)
        }
    }

    selected?.let { item ->
        MemoryDetailSheet(item = item, vm = vm, onDismiss = { selected = null })
    }
}

/** Draws sky, soil strata, the wandering taproot, rootlets, hair roots and nodes. */
@Composable
private fun RootsCanvas(memoryCount: Int, cx: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val soilYpx = SOIL_Y.toPx()
        val cxPx = cx.toPx()
        val rnd = seededRandom(1337)

        // Sky behind the canopy.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFEAF1E6), GrovePalette.bg),
                startY = 0f,
                endY = soilYpx,
            ),
            size = Size(size.width, soilYpx),
        )

        // Soil below the line.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(GrovePalette.soilTop, GrovePalette.soilBottom),
                startY = soilYpx,
                endY = size.height,
            ),
            topLeft = Offset(0f, soilYpx),
            size = Size(size.width, size.height - soilYpx),
        )

        // Faint strata lines.
        var strata = soilYpx + 80f
        while (strata < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.06f),
                start = Offset(0f, strata),
                end = Offset(size.width, strata + (rnd() - 0.5f) * 20f),
                strokeWidth = 2f,
            )
            strata += 120f + rnd() * 60f
        }

        // Speckles for soil texture.
        repeat(140) {
            val y = soilYpx + rnd() * (size.height - soilYpx)
            drawCircle(
                color = Color.Black.copy(alpha = 0.05f + rnd() * 0.05f),
                radius = 1.2f + rnd() * 2f,
                center = Offset(rnd() * size.width, y),
            )
        }

        // Grass tufts at the soil line.
        repeat(36) {
            val x = rnd() * size.width
            val h = 8f + rnd() * 12f
            drawLine(
                color = GrovePalette.foliage[1].copy(alpha = 0.8f),
                start = Offset(x, soilYpx),
                end = Offset(x + (rnd() - 0.5f) * 8f, soilYpx - h),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round,
            )
        }

        // Taproot: wandering, tapering points stepping down from the trunk base.
        val stepPx = 56.dp.toPx()
        val maxWander = 34.dp.toPx()
        val points = ArrayList<Offset>()
        var x = cxPx
        var y = soilYpx
        var width = 26f
        val widths = ArrayList<Float>()
        points.add(Offset(x, y)); widths.add(width)
        while (y < size.height - FOOTER.toPx() / 2f) {
            x += (rnd() - 0.5f) * 2f * 24.dp.toPx()
            x = x.coerceIn(cxPx - maxWander, cxPx + maxWander)
            y += stepPx
            width *= 0.93f
            points.add(Offset(x, y)); widths.add(width.coerceAtLeast(3f))
        }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = GrovePalette.rootColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = widths[i],
                cap = StrokeCap.Round,
            )
        }

        // Hair roots for texture.
        repeat(26) {
            val p = points[(rnd() * (points.size - 1)).toInt()]
            val dir = if (rnd() > 0.5f) 1f else -1f
            drawLine(
                color = GrovePalette.rootColor.copy(alpha = 0.6f),
                start = p,
                end = Offset(p.x + dir * (20f + rnd() * 40f), p.y + (rnd() - 0.2f) * 30f),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
        }

        // Rootlets + nodes to each memory card.
        val cardOffsetPx = CARD_OFFSET.toPx()
        val cardHalfPx = CARD_WIDTH.toPx() / 2f
        for (i in 0 until memoryCount) {
            val side = if (i % 2 == 0) -1 else 1
            val attachYpx = FIRST_MEMORY_Y.toPx() + MEMORY_GAP.toPx() * i
            val node = points.minByOrNull { abs(it.y - attachYpx) } ?: Offset(cxPx, attachYpx)
            val innerEdgeX = cxPx + side * (cardOffsetPx - cardHalfPx)

            val path = Path().apply {
                moveTo(node.x, node.y)
                quadraticBezierTo(
                    cxPx + side * cardOffsetPx * 0.5f,
                    (node.y + attachYpx) / 2f,
                    innerEdgeX,
                    attachYpx,
                )
            }
            drawPath(path, color = GrovePalette.rootColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
            drawCircle(GrovePalette.rootColor, radius = 6f, center = node)
        }
    }
}

@Composable
private fun RootMemoryCard(item: MemoryItem, onClick: () -> Unit) {
    val accent = GrovePalette.accentFor(item.type)
    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .clip(RoundedCornerShape(18.dp))
            .background(GrovePalette.surface)
            .clickable { onClick() }
            .padding(8.dp),
    ) {
        when (item.type) {
            "photo" -> {
                val image = item.image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = "Photo from ${item.sender}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(text = "📷", fontSize = 30.sp)
                    }
                }
            }

            "water" -> Box(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                contentAlignment = Alignment.Center,
            ) { Text(text = "💛", fontSize = 36.sp) }

            else -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Waveform(color = accent, modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${GrovePalette.glyphFor(item.type)} ${item.sender} · ${groveRelativeTime(item.epoch)}",
            color = GrovePalette.inkSoft,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
