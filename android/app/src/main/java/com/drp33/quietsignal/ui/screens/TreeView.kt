package com.drp33.quietsignal.ui.screens

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
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
    Color(0xFF81C784), // green
    Color(0xFF4FC3F7), // sky
    Color(0xFFFFB74D), // amber
    Color(0xFFBA68C8), // amethyst
    Color(0xFFF06292), // rose
    Color(0xFFFFD54F), // gold
    Color(0xFF4DB6AC), // teal
)

// ---- Species styling (only bark + branch shape; bead colours are separate) -

private data class Species(
    val barkTop: Color,
    val barkBottom: Color,
    val branchSpread: Float,
    val angleJitter: Float,
)

private fun speciesFor(type: Int): Species = when (((type % 3) + 3) % 3) {
    0 -> Species(Color(0xFF6D4C41), Color(0xFF3E2723), branchSpread = 0.60f, angleJitter = 0.35f)
    1 -> Species(Color(0xFF795548), Color(0xFF4E342E), branchSpread = 0.72f, angleJitter = 0.45f)
    else -> Species(Color(0xFF6D4C41), Color(0xFF3E2723), branchSpread = 0.52f, angleJitter = 0.30f)
}

// ---- Skeleton (built once; the whole tree just scales up over time) --------

private class Twig(
    val lengthFrac: Float,   // fraction of canvas height
    val angle: Float,        // radians; -TAU/4 points straight up
    val widthFrac: Float,    // stroke width as a fraction of canvas height
    val depth: Int,
    val children: List<Twig>,
    val isTip: Boolean,      // outer twig that can host beads
)

private fun buildTwig(angle: Float, lengthFrac: Float, widthFrac: Float, depth: Int, s: Species, rng: Random): Twig {
    if (depth >= MAX_DEPTH) {
        return Twig(lengthFrac, angle, widthFrac, depth, emptyList(), isTip = true)
    }
    val childCount = when (depth) {
        0 -> 1                    // single trunk
        1 -> 2 + rng.nextInt(2)   // 2..3 primary limbs
        else -> 2
    }
    val children = ArrayList<Twig>(childCount)
    for (i in 0 until childCount) {
        val a = if (depth == 0) {
            angle + (rng.nextFloat() - 0.5f) * 0.2f
        } else {
            angle + (i - (childCount - 1) / 2f) * s.branchSpread + (rng.nextFloat() - 0.5f) * s.angleJitter
        }
        val len = lengthFrac * (0.70f + rng.nextFloat() * 0.12f)
        children.add(buildTwig(a, len, widthFrac * 0.7f, depth + 1, s, rng))
    }
    return Twig(lengthFrac, angle, widthFrac, depth, children, isTip = depth >= MAX_DEPTH - 1)
}

// ---- Public composable -----------------------------------------------------

/** Tree drawing + a short caption. Used on both screens. */
@Composable
fun TreeSection(state: TreeState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TreeView(
            memoryCount = state.memoryCount,
            // Species variety is parked for now — a stable tree while we nail
            // the day-by-day growth + bead behaviour.
            treeType = 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
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
    memoryCount == 1 -> "🌿 1 memory growing"
    memoryCount < 6 -> "🌿 $memoryCount memories growing"
    else -> "🌳 $memoryCount memories and counting"
}

@Composable
private fun TreeView(memoryCount: Int, treeType: Int, modifier: Modifier) {
    // Size grows gradually with the number of memory-days and saturates, so it
    // starts as a small (but visible) sapling and never outgrows the canvas —
    // and it never resets.
    val sizeTarget = (1.0 - exp(-memoryCount / 8.0)).toFloat()
    val animSize by animateFloatAsState(sizeTarget, tween(1200, easing = FastOutSlowInEasing), label = "size")

    val sway by rememberInfiniteTransition(label = "sway").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway-value",
    )

    // Pop the newest bead in whenever a new day adds a memory.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(memoryCount) {
        if (memoryCount > 0) {
            pop.snapTo(0.2f)
            pop.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        }
    }

    val species = speciesFor(treeType)
    val skeleton = remember(treeType) {
        buildTwig(
            angle = -TAU / 4f,
            lengthFrac = 0.18f, // max size of a fully grown tree
            widthFrac = 0.05f,
            depth = 0,
            s = species,
            rng = Random(treeType * 7919 + 17),
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Calm twilight scene so the glowing beads read well.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF15282C), Color(0xFF1E3A36))), size = size)
        drawOval(
            color = Color(0xFF24433B),
            topLeft = Offset(-w * 0.25f, h * 0.86f),
            size = Size(w * 1.5f, h * 0.32f),
        )

        // 0.34 floor = small-but-visible sapling; grows to full (1.0) over time.
        val scale = 0.34f + 0.66f * animSize
        val base = Offset(w / 2f, h * 0.9f)
        val tips = ArrayList<Offset>()
        drawTwig(skeleton, base, scale, sway, h, species, tips)
        drawBeads(tips, memoryCount, scale, pop.value, treeType, h)
    }
}

// ---- Drawing ---------------------------------------------------------------

private fun DrawScope.drawTwig(
    twig: Twig,
    start: Offset,
    scale: Float,
    sway: Float,
    h: Float,
    s: Species,
    tips: MutableList<Offset>,
) {
    val swayed = twig.angle + sway * 0.04f * (twig.depth + 1) // upper twigs sway more
    val len = twig.lengthFrac * h * scale
    val end = Offset(start.x + cos(swayed) * len, start.y + sin(swayed) * len)

    // A gentle curve makes the bark organic rather than a straight stick.
    val ctrl = Offset((start.x + end.x) / 2f - sin(swayed) * len * 0.10f, (start.y + end.y) / 2f)
    val path = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(ctrl.x, ctrl.y, end.x, end.y)
    }
    val width = (twig.widthFrac * h * scale).coerceAtLeast(2f)
    // Pseudo-3D cylinder: dark base stroke + a brighter, thinner core ridge.
    drawPath(
        path = path,
        brush = Brush.linearGradient(listOf(s.barkBottom, s.barkTop), start, end),
        style = Stroke(width = width, cap = StrokeCap.Round),
    )
    drawPath(
        path = path,
        color = lerp(s.barkTop, Color.White, 0.22f),
        style = Stroke(width = (width * 0.42f).coerceAtLeast(1f), cap = StrokeCap.Round),
    )

    if (twig.isTip) tips.add(end)
    twig.children.forEach { drawTwig(it, end, scale, sway, h, s, tips) }
}

private fun DrawScope.drawBeads(
    tips: List<Offset>,
    memoryCount: Int,
    scale: Float,
    pop: Float,
    treeType: Int,
    h: Float,
) {
    if (tips.isEmpty() || memoryCount <= 0) return

    val shown = min(memoryCount, BEAD_CAP)
    val rng = Random(treeType * 9173 + 7)
    val baseR = (0.022f * h).coerceIn(5f, 13f)

    for (i in 0 until shown) {
        // Round-robin across *all* tips so beads spread evenly and one new bead
        // lands per day; later wraps sit a touch further out (a fuller canopy).
        val tip = tips[i % tips.size]
        val ring = i / tips.size
        // Consume rng in a fixed order so each bead's slot stays put frame-to-frame.
        val ang = rng.nextFloat() * TAU
        val radBase = rng.nextFloat()
        val sizeJ = rng.nextFloat()
        val colour = BEAD_PALETTE[rng.nextInt(BEAD_PALETTE.size)]

        val rad = (0.012f + 0.022f * radBase + ring * 0.016f) * h * scale
        val pos = Offset(tip.x + cos(ang) * rad, tip.y + sin(ang) * rad)
        var r = (baseR * scale * (0.75f + sizeJ * 0.5f)).coerceAtLeast(4.5f)
        if (i == shown - 1) r *= pop // newest bead pops in

        drawBead(pos, r, colour)
    }
}

/** A glossy, lit-from-top-left 3D orb. */
private fun DrawScope.drawBead(center: Offset, r: Float, colour: Color) {
    if (r <= 0.5f) return
    // soft contact shadow
    drawCircle(Color.Black.copy(alpha = 0.22f), r * 0.95f, Offset(center.x + r * 0.18f, center.y + r * 0.25f))
    // ambient glow
    drawCircle(
        brush = Brush.radialGradient(listOf(colour.copy(alpha = 0.5f), Color.Transparent), center = center, radius = r * 2.4f),
        radius = r * 2.4f,
        center = center,
        blendMode = BlendMode.Screen,
    )
    // spherical body
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lerp(colour, Color.White, 0.65f), colour, lerp(colour, Color.Black, 0.45f)),
            center = Offset(center.x - r * 0.35f, center.y - r * 0.35f),
            radius = r * 1.5f,
        ),
        radius = r,
        center = center,
    )
    // specular highlight
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.22f, Offset(center.x - r * 0.36f, center.y - r * 0.36f))
}
