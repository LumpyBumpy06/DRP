package com.drp33.quietsignal.ui.screens

/*
 * GroveTree – a procedural, Canvas-drawn tree that is a drop-in replacement for
 * WateringTree(stage, deathLevel, modifier). No Lottie, no assets: the tree is
 * generated from a fixed seed so Norman and Sadie always see the same shape.
 *
 *   stage      : 0..5 from /tree  -> how full the canopy is
 *   deathLevel : 0..1 from /tree  -> desaturates toward brown + thins the canopy
 *                                    (a calm "resting/wilting", never a bare stick)
 *   blossoms   : optional – one flower per recent memory (voice/photo/water/note),
 *                colour-cycled. Pass memoryCount or a typed list later.
 *
 * Responsive: the tree is authored in a 400x540 "design space" and scaled to the
 * Canvas size, bottom-anchored, so it fills any width/height.
 *
 * This file is self-contained (only Compose + kotlin.math). Paste it into
 * ui/screens/ and call GroveTree(...) where WateringTree(...) is used today.
 */

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
//  Palette (Botanical). Swap these three foliage colours + trunk for the
//  "Clean" or "Storybook" looks if you theme it later.
// ---------------------------------------------------------------------------
private val FOLIAGE = listOf(
    Color(0xFF5E8A5C), Color(0xFF477049), Color(0xFF7BA46F),
)
private val FOLIAGE_REST = Color(0xFFA7AC8E)
private val FOLIAGE_DEAD = Color(0xFF8A7A4C)
private val TRUNK_TOP = Color(0xFF8A7250)
private val TRUNK_BOT = Color(0xFF6B563A)
private val SURFACE = Color(0xFFFBF8F0)

// Blossom colours by memory type index: voice, photo, water, note
private val BLOSSOM = listOf(
    Color(0xFF8579B0), Color(0xFFC26B4E), Color(0xFF5E93AE), Color(0xFF869B5A),
)

// ---------------------------------------------------------------------------
//  Seeded RNG – identical sequence to the web prototype's mulberry32.
// ---------------------------------------------------------------------------
private fun mulberry32(seed: Int): () -> Float {
    var a = seed
    return {
        a += 0x6D2B79F5.toInt()
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
}

// ---------------------------------------------------------------------------
//  Geometry (design space 400 x 540, ground at y=470)
// ---------------------------------------------------------------------------
private class Branch(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val w: Float, val depth: Int)
private class Tip(val x: Float, val y: Float, val score: Float)
private class Anchor(val x: Float, val y: Float, val r: Float, val score: Float)
private class TreeGeom(val branches: List<Branch>, val tips: List<Tip>, val anchors: List<Anchor>)

private fun buildTree(seed: Int): TreeGeom {
    val rnd = mulberry32(seed)
    val branches = ArrayList<Branch>()
    val tips = ArrayList<Tip>()
    val baseX = 200f
    val baseY = 470f

    fun grow(x: Float, y: Float, angle: Float, len: Float, width: Float, depth: Int) {
        val x2 = x + sin(angle) * len
        val y2 = y - cos(angle) * len
        branches.add(Branch(x, y, x2, y2, width, depth))
        if (depth <= 0 || len < 16f) {
            tips.add(Tip(x2, y2, 0f))
            return
        }
        val n = if (depth >= 4) 2 else if (rnd() > 0.45f) 2 else 3
        val spread = 0.34f + rnd() * 0.26f
        for (i in 0 until n) {
            val t = if (n == 1) 0f else (i / (n - 1f) - 0.5f) * 2f
            val a = angle + t * spread + (rnd() - 0.5f) * 0.18f
            val l = len * (0.7f + rnd() * 0.14f)
            grow(x2, y2, a, l, max(1.6f, width * 0.68f), depth - 1)
        }
    }
    grow(baseX, baseY, (rnd() - 0.5f) * 0.1f, 96f, 17f, 5)

    // reveal order: inner/low first -> outward & up
    val scoredTips = tips.map { tp ->
        Tip(tp.x, tp.y, abs(tp.x - baseX) * 0.7f + (470f - tp.y) * 0.5f + rnd() * 30f)
    }.sortedBy { it.score }

    // foliage anchors: tips + a few mid-canopy fillers
    val rnd2 = mulberry32(seed + 99)
    val anchors = ArrayList<Anchor>()
    scoredTips.forEach { tp -> anchors.add(Anchor(tp.x, tp.y, 24f + rnd2() * 14f, tp.score)) }
    branches.filter { it.depth <= 2 }.forEach { b ->
        if (rnd2() > 0.5f) {
            anchors.add(
                Anchor((b.x1 + b.x2) / 2f, (b.y1 + b.y2) / 2f, 18f + rnd2() * 10f, (b.depth + 1) * 40f + rnd2() * 60f),
            )
        }
    }
    anchors.sortBy { it.score }
    return TreeGeom(branches, scoredTips, anchors)
}

// ---------------------------------------------------------------------------
//  Composable
// ---------------------------------------------------------------------------
@Composable
fun GroveTree(
    stage: Int,
    deathLevel: Float,
    modifier: Modifier = Modifier,
    seed: Int = 42,
    blossomCount: Int = 0,
    height: Dp = 300.dp,
    onBlossomTap: ((Int) -> Unit)? = null,
) {
    val tree = remember(seed) { buildTree(seed) }
    val death = deathLevel.coerceIn(0f, 1f)

    // activity (canopy fullness) eases when stage changes
    val targetActivity = (0.2f + (stage.coerceIn(0, 5) / 5f) * 0.8f)
    val activity by animateFloatAsState(
        targetValue = targetActivity * (1f - death * 0.65f),
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "grove-activity",
    )

    // gentle life: a slow canopy sway
    val transition = rememberInfiniteTransition(label = "grove-life")
    val sway by transition.animateFloat(
        initialValue = -0.018f, targetValue = 0.018f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "grove-sway",
    )

    val resting = activity < 0.3f

    val tapModifier = if (onBlossomTap != null) {
        Modifier.pointerInput(blossomCount, tree) {
            detectTapGestures { tap ->
                val centers = groveBlossomCenters(tree, blossomCount, size.width.toFloat(), size.height.toFloat())
                val sc = groveScale(size.width.toFloat(), size.height.toFloat())
                // Index of the tapped bloom, or -1 for a tap elsewhere on the tree.
                val hit = centers.indexOfFirst { (it - tap).getDistance() <= 22f * sc.s }
                onBlossomTap(hit)
            }
        }
    } else {
        Modifier
    }

    Box(modifier.fillMaxWidth().height(height).then(tapModifier)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            // Scale the 400×540 design to *fit* the box (so it never overflows the
            // header), bottom-anchored and horizontally centred.
            val sc = groveScale(size.width, size.height)
            val s = sc.s
            fun px(x: Float, y: Float) = sc.px(x, y)

            val trunkBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(lerpDeath(TRUNK_TOP, death), lerpDeath(TRUNK_BOT, death)),
                start = px(160f, 0f), end = px(240f, 0f),
            )

            // ground shadow
            drawOval(
                color = Color(0xFFDED3BC),
                topLeft = px(80f, 461f), size = androidx.compose.ui.geometry.Size(240f * s, 30f * s),
                alpha = 0.85f,
            )

            // branches
            tree.branches.forEach { b ->
                drawLine(trunkBrush, px(b.x1, b.y1), px(b.x2, b.y2), strokeWidth = b.w * s, cap = StrokeCap.Round)
            }

            // foliage – fills with activity, sways slightly around the trunk top
            val shownCount = max(
                if (resting) (tree.anchors.size * 0.32f).toInt() else 3,
                (tree.anchors.size * min(1f, 0.25f + activity * 0.85f)).toInt(),
            )
            val pivot = px(200f, 300f)
            rotateRad(sway, pivot) {
                tree.anchors.take(shownCount).forEachIndexed { i, a ->
                    val c0 = foliageColor(i % 3, resting, death)
                    val c1 = foliageColor((i + 1) % 3, resting, death)
                    val c2 = foliageColor((i + 2) % 3, resting, death)
                    val center = px(a.x, a.y)
                    drawCircle(c0, a.r * s, center, alpha = if (resting) 0.7f else 0.96f)
                    drawCircle(c1, a.r * 0.62f * s, Offset(center.x - a.r * 0.35f * s, center.y - a.r * 0.3f * s), alpha = 0.9f)
                    drawCircle(c2, a.r * 0.5f * s, Offset(center.x + a.r * 0.3f * s, center.y + a.r * 0.15f * s), alpha = 0.85f)
                }
            }

            // blossoms – one flower per recent memory, spread across the canopy
            if (blossomCount > 0 && tree.tips.isNotEmpty()) {
                val stride = max(1, tree.tips.size / blossomCount)
                for (i in 0 until blossomCount) {
                    val tp = tree.tips[(i * stride) % tree.tips.size]
                    drawBlossom(px(tp.x, tp.y), BLOSSOM[i % BLOSSOM.size], s)
                }
            }
        }
    }
}

/** The fitted scale + offsets mapping the 400×540 design space onto a canvas. */
private class GroveScale(val s: Float, val dx: Float, val dy: Float) {
    fun px(x: Float, y: Float) = Offset(x * s + dx, y * s + dy)
}

private fun groveScale(w: Float, h: Float): GroveScale {
    val s = min(w / 400f, h / 540f)
    return GroveScale(s, (w - 400f * s) / 2f, h - 540f * s)
}

/** Screen-space centres of the blossoms, so taps can be hit-tested to a memory. */
private fun groveBlossomCenters(tree: TreeGeom, blossomCount: Int, w: Float, h: Float): List<Offset> {
    if (blossomCount <= 0 || tree.tips.isEmpty()) return emptyList()
    val sc = groveScale(w, h)
    val stride = max(1, tree.tips.size / blossomCount)
    return (0 until blossomCount).map { i ->
        val tp = tree.tips[(i * stride) % tree.tips.size]
        sc.px(tp.x, tp.y)
    }
}

private fun DrawScope.drawBlossom(center: Offset, color: Color, s: Float) {
    drawCircle(color, 18f * s, center, alpha = 0.22f)            // glow
    drawCircle(SURFACE, 11f * s, center, alpha = 0.92f)          // white seat
    for (k in 0 until 5) {
        val ang = Math.toRadians((k * 72).toDouble()).toFloat()
        val pc = Offset(center.x + cos(ang) * 6.6f * s, center.y + sin(ang) * 6.6f * s)
        drawCircle(color, 4.4f * s, pc, alpha = 0.96f)           // petals (round, simple)
    }
    drawCircle(SURFACE, 4.2f * s, center)                        // center
    drawCircle(color, 2f * s, center, alpha = 0.6f)
}

private fun foliageColor(i: Int, resting: Boolean, death: Float): Color {
    val base = if (resting) FOLIAGE_REST else FOLIAGE[i]
    return lerp(base, FOLIAGE_DEAD, death * 0.8f)
}

private fun lerpDeath(c: Color, death: Float): Color = lerp(c, FOLIAGE_DEAD, death * 0.5f)
