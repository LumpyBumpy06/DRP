package com.drp33.quietsignal.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drp33.quietsignal.model.TreeState
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private data class LeafPalette(val full: Color, val fading: Color)

private fun paletteFor(type: Int): LeafPalette = when (((type % 3) + 3) % 3) {
    0 -> LeafPalette(Color(0xFF43A047), Color(0xFF9E9D24)) // green
    1 -> LeafPalette(Color(0xFFF06292), Color(0xFFAD8FBF)) // cherry blossom
    else -> LeafPalette(Color(0xFFFB8C00), Color(0xFF8D4E2A)) // autumn
}

/** Tree drawing + a short motivational caption. Used on both screens. */
@Composable
fun TreeSection(state: TreeState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TreeView(
            growth = state.growth,
            leafiness = state.leafiness,
            treeType = state.treeType,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = treeCaption(state.growth, state.leafiness),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun treeCaption(growth: Float, leafiness: Float): String = when {
    leafiness < 0.4f -> "🍂 The tree is shedding leaves — a check-in will revive it"
    growth < 0.12f -> "🌱 A fresh sapling — stay in touch to help it grow"
    leafiness > 0.85f && growth > 0.6f -> "🌳 Your tree is thriving!"
    else -> "🌿 Growing strong — keep it up"
}

@Composable
private fun TreeView(growth: Float, leafiness: Float, treeType: Int, modifier: Modifier) {
    val animGrowth by animateFloatAsState(growth.coerceIn(0f, 1f), tween(1500), label = "growth")
    val animLeaf by animateFloatAsState(leafiness.coerceIn(0f, 1f), tween(1500), label = "leaf")
    val sway by rememberInfiniteTransition(label = "sway").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway-value",
    )

    val palette = paletteFor(treeType)
    val bark = Color(0xFF6D4C41)
    val ground = Color(0xFF8D6E63)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawLine(ground, Offset(0f, h * 0.93f), Offset(w, h * 0.93f), strokeWidth = 5f)

        val maxDepth = (3 + animGrowth * 4).roundToInt()
        val trunkLen = h * (0.14f + 0.18f * animGrowth)
        val trunkWidth = 7f + 13f * animGrowth

        // Branch shape is fully deterministic (seeded by species) so only the
        // sway moves frame-to-frame. Leaves use a separate per-cluster seed, so
        // changing leafiness never reshapes the branches.
        val branchRng = Random(treeType * 7919 + 17)
        val leafCounter = intArrayOf(0)

        drawBranch(
            start = Offset(w / 2f, h * 0.93f),
            angle = 0f,
            length = trunkLen,
            width = trunkWidth,
            depth = 0,
            maxDepth = maxDepth,
            leafiness = animLeaf,
            palette = palette,
            bark = bark,
            sway = sway,
            branchRng = branchRng,
            leafCounter = leafCounter,
        )

        if (animLeaf < 0.85f) {
            val fallen = ((1f - animLeaf) * 16).roundToInt()
            val groundRng = Random(treeType * 104729 + 3)
            repeat(fallen) {
                val fx = w * (0.18f + 0.64f * groundRng.nextFloat())
                val fy = h * (0.9f + 0.03f * groundRng.nextFloat())
                drawOval(palette.fading.copy(alpha = 0.7f), topLeft = Offset(fx, fy), size = Size(11f, 6f))
            }
        }
    }
}

private fun DrawScope.drawBranch(
    start: Offset,
    angle: Float,
    length: Float,
    width: Float,
    depth: Int,
    maxDepth: Int,
    leafiness: Float,
    palette: LeafPalette,
    bark: Color,
    sway: Float,
    branchRng: Random,
    leafCounter: IntArray,
) {
    val swayed = angle + sway * 0.03f * depth // upper branches sway more
    val end = Offset(start.x + sin(swayed) * length, start.y - cos(swayed) * length)
    drawLine(bark, start, end, strokeWidth = width.coerceAtLeast(2f), cap = StrokeCap.Round)

    if (depth >= maxDepth - 1) {
        drawLeafCluster(end, leafiness, palette, leafCounter)
    }
    if (depth >= maxDepth) return

    val spread = 0.32f + 0.12f * branchRng.nextFloat()
    val shrink = 0.72f + 0.06f * branchRng.nextFloat()
    val j1 = (branchRng.nextFloat() - 0.5f) * 0.12f
    val j2 = (branchRng.nextFloat() - 0.5f) * 0.12f

    drawBranch(end, swayed - spread + j1, length * shrink, width * 0.68f, depth + 1, maxDepth, leafiness, palette, bark, sway, branchRng, leafCounter)
    drawBranch(end, swayed + spread + j2, length * shrink, width * 0.68f, depth + 1, maxDepth, leafiness, palette, bark, sway, branchRng, leafCounter)

    val mid = branchRng.nextFloat()
    if (depth in 1..2 && mid < 0.55f) {
        val j3 = (branchRng.nextFloat() - 0.5f) * 0.25f
        drawBranch(end, swayed + j3, length * shrink * 0.88f, width * 0.6f, depth + 1, maxDepth, leafiness, palette, bark, sway, branchRng, leafCounter)
    }
}

private fun DrawScope.drawLeafCluster(
    center: Offset,
    leafiness: Float,
    palette: LeafPalette,
    leafCounter: IntArray,
) {
    val id = leafCounter[0]
    leafCounter[0] = id + 1 // advance even when bare, so cluster seeds stay stable
    if (leafiness <= 0.03f) return

    val leafRng = Random(id + 1)
    val count = (2 + leafiness * 6).roundToInt()
    val color = lerp(palette.fading, palette.full, leafiness).copy(alpha = 0.45f + 0.5f * leafiness)
    repeat(count) {
        val ox = (leafRng.nextFloat() - 0.5f) * 30f
        val oy = (leafRng.nextFloat() - 0.5f) * 30f
        drawOval(color, topLeft = Offset(center.x + ox - 6f, center.y + oy - 4f), size = Size(13f, 9f))
    }
}
