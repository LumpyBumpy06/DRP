package com.drp33.quietsignal.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drp33.quietsignal.model.TreeState
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = 6.2831855f
private const val MAX_DEPTH = 5
private const val BEAD_CAP = 80

// Each memory-bead is a little jewel; varied colours so the canopy feels alive.
private val BEAD_PALETTE = listOf(
    Color(0xFF81C784), Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFFBA68C8),
    Color(0xFFF06292), Color(0xFFFFD54F), Color(0xFF4DB6AC),
)

private val BARK_DARK = Color(0xFF3E2723)
private val BARK_LIGHT = Color(0xFF6D4C41)

// ---- 3D maths --------------------------------------------------------------

private class V3(val x: Float, val y: Float, val z: Float)

private operator fun V3.plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
private operator fun V3.times(s: Float) = V3(x * s, y * s, z * s)
private fun mix(a: V3, b: V3, t: Float) = V3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)

private class Seg(val a: V3, val b: V3, val w: Float)

/**
 * Builds a tree in 3D (Y up). Branches start at *random points along* their
 * parent and fan out in random azimuths, so it's organic rather than a
 * symmetric binary tree. Returns the segments plus canopy anchor points.
 */
private fun generateTree(seed: Int): Pair<List<Seg>, List<V3>> {
    val rng = Random(seed)
    val segs = ArrayList<Seg>()
    val anchors = ArrayList<V3>()

    fun grow(start: V3, theta: Float, phi: Float, length: Float, width: Float, depth: Int) {
        val dir = V3(sin(theta) * cos(phi), cos(theta), sin(theta) * sin(phi))
        val end = start + dir * length
        segs.add(Seg(start, end, width))
        if (depth >= 3) anchors.add(end)
        if (depth >= MAX_DEPTH || length < 0.06f) return

        val n = when (depth) {
            0 -> 3 + rng.nextInt(2)    // 3..4 main limbs off the trunk
            1, 2 -> 2 + rng.nextInt(2) // 2..3
            else -> 1 + rng.nextInt(2) // 1..2
        }
        for (k in 0 until n) {
            val t = 0.5f + rng.nextFloat() * 0.5f        // start fraction ALONG the parent (randomised)
            val cStart = mix(start, end, t)
            val spread = (if (depth == 0) 0.55f else 0.42f) + rng.nextFloat() * 0.2f
            val cTheta = (theta + spread).coerceAtMost(1.45f)
            val cPhi = if (depth == 0) {
                k * (TAU / n) + (rng.nextFloat() - 0.5f) * 0.5f          // limbs fan around the full circle
            } else {
                phi + (k - (n - 1) / 2f) * 0.7f + (rng.nextFloat() - 0.5f) * 0.4f
            }
            grow(cStart, cTheta, cPhi, length * (0.60f + rng.nextFloat() * 0.18f), width * 0.68f, depth + 1)
        }
    }

    grow(V3(0f, 0f, 0f), 0f, 0f, 1.0f, 0.05f, 0)
    return segs to anchors
}

// ---- Public composable -----------------------------------------------------

/** Tree drawing + a short caption. Used on both screens. */
@Composable
fun TreeSection(state: TreeState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TreeView(
            memoryCount = state.memoryCount,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(20.dp)),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = treeCaption(state.memoryCount),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun treeCaption(memoryCount: Int): String = when {
    memoryCount == 0 -> "🌱 A fresh sapling — check in to plant your first memory"
    memoryCount == 1 -> "🌿 1 memory · drag to spin the tree"
    memoryCount < 6 -> "🌿 $memoryCount memories · drag to spin the tree"
    else -> "🌳 $memoryCount memories · drag to spin the tree"
}

private class BeadHit(val c: Offset, val r: Float, val index: Int)
private class PSeg(val a: Offset, val b: Offset, val w: Float, val z: Float)
private class PBead(val c: Offset, val r: Float, val z: Float, val colour: Color, val index: Int)

@Composable
private fun TreeView(memoryCount: Int, modifier: Modifier) {
    // Size grows gradually with days and saturates, so it starts small but
    // visible and never outgrows the frame or resets.
    val sizeTarget = (1.0 - exp(-memoryCount / 8.0)).toFloat()
    val animSize by animateFloatAsState(sizeTarget, tween(1200, easing = FastOutSlowInEasing), label = "size")

    var rotation by remember { mutableFloatStateOf(0.6f) } // slight turn so depth reads immediately
    var selected by remember { mutableIntStateOf(-1) }

    // Pop the newest bead in when a new day adds a memory.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(memoryCount) {
        if (memoryCount > 0) {
            pop.snapTo(0.2f)
            pop.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        }
    }

    val (segs, anchors) = remember { generateTree(seed = 20260604) }
    // Shuffled fill order so the first N beads spread around the whole canopy.
    val beadOrder = remember { anchors.indices.shuffled(Random(99)) }
    // Bead hit-boxes from the latest frame, read by the tap handler.
    val hits = remember { ArrayList<BeadHit>() }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    rotation += drag.x * 0.01f
                    change.consume()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    selected = hits
                        .filter { (it.c - tap).getDistance() <= it.r * 1.8f }
                        .minByOrNull { (it.c - tap).getDistanceSquared() }
                        ?.index ?: -1
                }
            },
    ) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF15282C), Color(0xFF1E3A36))), size = size)
        drawOval(Color(0xFF24433B), Offset(-w * 0.25f, h * 0.86f), Size(w * 1.5f, h * 0.32f))

        val sizeScale = 0.34f + 0.66f * animSize
        val s = h * 0.30f * sizeScale // unit -> px
        val focal = 4.2f
        val cx = w / 2f
        val cy = h * 0.9f
        val rot = rotation

        // Branches: project, depth-sort, draw far -> near.
        segs.map { seg ->
            val (a, za) = project(seg.a, rot, cx, cy, s, focal)
            val (b, zb) = project(seg.b, rot, cx, cy, s, focal)
            PSeg(a, b, seg.w * s * persp((za + zb) / 2f, focal), (za + zb) / 2f)
        }.sortedBy { it.z }.forEach { ps ->
            val wpx = ps.w.coerceAtLeast(1.5f)
            drawLine(BARK_DARK, ps.a, ps.b, strokeWidth = wpx, cap = StrokeCap.Round)
            drawLine(lerp(BARK_LIGHT, Color.White, 0.18f), ps.a, ps.b, strokeWidth = (wpx * 0.42f).coerceAtLeast(1f), cap = StrokeCap.Round)
        }

        // Foliage: soft canopy puffs that fill in as the tree grows.
        val foliageAlpha = 0.30f * animSize
        if (foliageAlpha > 0.03f) {
            anchors.indices.filter { it % 2 == 0 }
                .map { project(anchors[it], rot, cx, cy, s, focal) }
                .sortedBy { it.second }
                .forEach { (pos, z) ->
                    val rr = (0.05f * s * persp(z, focal)).coerceIn(6f, 42f)
                    drawCircle(
                        brush = Brush.radialGradient(listOf(Color(0xFF5E7E55).copy(alpha = foliageAlpha), Color.Transparent), center = pos, radius = rr),
                        radius = rr,
                        center = pos,
                    )
                }
        }

        // Beads: one per day on shuffled anchors, projected, depth-sorted.
        hits.clear()
        val shown = min(memoryCount, BEAD_CAP)
        val rng = Random(7)
        val beadBaseR = (0.02f * h).coerceIn(5f, 12f)
        val beads = ArrayList<PBead>(shown)
        for (i in 0 until shown) {
            val anchor = anchors[beadOrder[i % beadOrder.size]]
            val ring = i / beadOrder.size
            val ox = (rng.nextFloat() - 0.5f) * 0.06f
            val oy = (rng.nextFloat() - 0.5f) * 0.06f
            val oz = (rng.nextFloat() - 0.5f) * 0.06f
            val sizeJ = rng.nextFloat()
            val colour = BEAD_PALETTE[rng.nextInt(BEAD_PALETTE.size)]
            val p = anchor + V3(ox, oy + ring * 0.05f, oz)
            val (pos, z) = project(p, rot, cx, cy, s, focal)
            var r = (beadBaseR * persp(z, focal) * sizeScale * (0.8f + sizeJ * 0.5f)).coerceAtLeast(4.5f)
            if (i == shown - 1) r *= pop.value // newest bead pops in
            beads.add(PBead(pos, r, z, colour, i))
        }
        beads.sortBy { it.z }
        beads.forEach { b ->
            drawBead(b.c, b.r, b.colour)
            if (b.index == selected) {
                drawCircle(Color.White.copy(alpha = 0.9f), b.r * 1.6f, b.c, style = Stroke(width = 3f))
            }
            hits.add(BeadHit(b.c, b.r, b.index))
        }
    }
}

// ---- Drawing helpers -------------------------------------------------------

/** Rotate around the vertical (Y) axis and project with mild perspective. */
private fun project(p: V3, rot: Float, cx: Float, cy: Float, s: Float, focal: Float): Pair<Offset, Float> {
    val c = cos(rot)
    val sn = sin(rot)
    val rx = p.x * c + p.z * sn
    val rz = -p.x * sn + p.z * c
    val pp = focal / (focal - rz)
    return Offset(cx + rx * s * pp, cy - p.y * s * pp) to rz
}

private fun persp(z: Float, focal: Float): Float = focal / (focal - z)

/** A glossy, lit-from-top-left 3D orb. */
private fun DrawScope.drawBead(center: Offset, r: Float, colour: Color) {
    if (r <= 0.5f) return
    drawCircle(Color.Black.copy(alpha = 0.22f), r * 0.95f, Offset(center.x + r * 0.18f, center.y + r * 0.25f))
    drawCircle(
        brush = Brush.radialGradient(listOf(colour.copy(alpha = 0.5f), Color.Transparent), center = center, radius = r * 2.4f),
        radius = r * 2.4f,
        center = center,
        blendMode = BlendMode.Screen,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lerp(colour, Color.White, 0.65f), colour, lerp(colour, Color.Black, 0.45f)),
            center = Offset(center.x - r * 0.35f, center.y - r * 0.35f),
            radius = r * 1.5f,
        ),
        radius = r,
        center = center,
    )
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.22f, Offset(center.x - r * 0.36f, center.y - r * 0.36f))
}
