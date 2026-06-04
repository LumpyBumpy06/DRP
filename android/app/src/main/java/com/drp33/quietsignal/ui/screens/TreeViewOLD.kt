// package com.drp33.quietsignal.ui.screens

// import androidx.compose.animation.core.FastOutSlowInEasing
// import androidx.compose.animation.core.RepeatMode
// import androidx.compose.animation.core.animateFloat
// import androidx.compose.animation.core.animateFloatAsState
// import androidx.compose.animation.core.infiniteRepeatable
// import androidx.compose.animation.core.rememberInfiniteTransition
// import androidx.compose.animation.core.tween
// import androidx.compose.foundation.Canvas
// import androidx.compose.foundation.layout.Column
// import androidx.compose.foundation.layout.Spacer
// import androidx.compose.foundation.layout.fillMaxWidth
// import androidx.compose.foundation.layout.height
// import androidx.compose.foundation.shape.RoundedCornerShape
// import androidx.compose.material3.MaterialTheme
// import androidx.compose.material3.Text
// import androidx.compose.runtime.Composable
// import androidx.compose.runtime.getValue
// import androidx.compose.runtime.remember
// import androidx.compose.ui.Alignment
// import androidx.compose.ui.Modifier
// import androidx.compose.ui.draw.clip
// import androidx.compose.ui.geometry.Offset
// import androidx.compose.ui.geometry.Size
// import androidx.compose.ui.graphics.BlendMode
// import androidx.compose.ui.graphics.Brush
// import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.graphics.Path
// import androidx.compose.ui.graphics.StrokeCap
// import androidx.compose.ui.graphics.drawscope.DrawScope
// import androidx.compose.ui.graphics.drawscope.Stroke
// import androidx.compose.ui.graphics.lerp
// import androidx.compose.ui.text.style.TextAlign
// import androidx.compose.ui.unit.dp
// import com.drp33.quietsignal.model.TreeState
// import kotlin.math.cos
// import kotlin.math.min
// import kotlin.math.roundToInt
// import kotlin.math.sin
// import kotlin.random.Random

// private const val TAU = 6.2831855f
// private const val MAX_DEPTH = 5
// private const val LEAF_CAP = 80

// // ---- Species styling -------------------------------------------------------

// private data class Species(
//     val leaf: Color,        // healthy leaf
//     val leafFading: Color,  // browning leaf when leafiness is low
//     val barkTop: Color,
//     val barkBottom: Color,
//     val branchSpread: Float,
//     val angleJitter: Float,
// )

// private fun speciesFor(type: Int): Species = when (((type % 3) + 3) % 3) {
//     0 -> Species( // lush green
//         leaf = Color(0xFF66BB6A), leafFading = Color(0xFFB39A3E),
//         barkTop = Color(0xFF6D4C41), barkBottom = Color(0xFF3E2723),
//         branchSpread = 0.60f, angleJitter = 0.35f,
//     )
//     1 -> Species( // cherry blossom
//         leaf = Color(0xFFF48FB1), leafFading = Color(0xFFB58FA6),
//         barkTop = Color(0xFF795548), barkBottom = Color(0xFF4E342E),
//         branchSpread = 0.72f, angleJitter = 0.45f,
//     )
//     else -> Species( // autumn maple
//         leaf = Color(0xFFFFB74D), leafFading = Color(0xFF8D5A2B),
//         barkTop = Color(0xFF6D4C41), barkBottom = Color(0xFF3E2723),
//         branchSpread = 0.52f, angleJitter = 0.30f,
//     )
// }

// // ---- Skeleton (built once per species; growth only *reveals* it) -----------

// private class Twig(
//     val lengthFrac: Float,   // fraction of canvas height
//     val angle: Float,        // radians; -TAU/4 points straight up
//     val widthFrac: Float,    // stroke width as a fraction of canvas height
//     val depth: Int,
//     val children: List<Twig>,
//     val isTip: Boolean,      // outer twig that hosts leaves
// )

// private fun buildTwig(angle: Float, lengthFrac: Float, widthFrac: Float, depth: Int, s: Species, rng: Random): Twig {
//     if (depth >= MAX_DEPTH) {
//         return Twig(lengthFrac, angle, widthFrac, depth, emptyList(), isTip = true)
//     }
//     val childCount = when (depth) {
//         0 -> 1                    // single trunk
//         1 -> 2 + rng.nextInt(2)   // 2..3 primary limbs
//         else -> 2
//     }
//     val children = ArrayList<Twig>(childCount)
//     for (i in 0 until childCount) {
//         val a = if (depth == 0) {
//             angle + (rng.nextFloat() - 0.5f) * 0.2f
//         } else {
//             angle + (i - (childCount - 1) / 2f) * s.branchSpread + (rng.nextFloat() - 0.5f) * s.angleJitter
//         }
//         val len = lengthFrac * (0.70f + rng.nextFloat() * 0.12f)
//         children.add(buildTwig(a, len, widthFrac * 0.7f, depth + 1, s, rng))
//     }
//     return Twig(lengthFrac, angle, widthFrac, depth, children, isTip = depth >= MAX_DEPTH - 1)
// }

// // ---- Public composable -----------------------------------------------------

// /** Tree drawing + a short motivational caption. Used on both screens. */
// @Composable
// fun TreeSection(state: TreeState, modifier: Modifier = Modifier) {
//     Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
//         TreeView(
//             growth = state.growth,
//             leafiness = state.leafiness,
//             treeType = state.treeType,
//             memoryCount = state.memoryCount,
//             modifier = Modifier
//                 .fillMaxWidth()
//                 .height(260.dp)
//                 .clip(RoundedCornerShape(20.dp)),
//         )
//         Spacer(modifier = Modifier.height(8.dp))
//         Text(
//             text = treeCaption(state.growth, state.leafiness, state.memoryCount),
//             style = MaterialTheme.typography.bodyMedium,
//             textAlign = TextAlign.Center,
//         )
//     }
// }

// private fun treeCaption(growth: Float, leafiness: Float, memoryCount: Int): String = when {
//     leafiness < 0.4f -> "🍂 The tree is shedding leaves — a check-in will revive it"
//     growth < 0.12f && memoryCount == 0 -> "🌱 A fresh sapling — stay in touch to help it grow"
//     leafiness > 0.85f && growth > 0.6f -> "🌳 Thriving — $memoryCount shared moments and counting"
//     else -> "🌿 Growing strong — keep it up"
// }

// @Composable
// private fun TreeView(growth: Float, leafiness: Float, treeType: Int, memoryCount: Int, modifier: Modifier) {
//     val animGrowth by animateFloatAsState(growth.coerceIn(0f, 1f), tween(1500), label = "growth")
//     val animLeaf by animateFloatAsState(leafiness.coerceIn(0f, 1f), tween(1500), label = "leaf")
//     val sway by rememberInfiniteTransition(label = "sway").animateFloat(
//         initialValue = -1f,
//         targetValue = 1f,
//         animationSpec = infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
//         label = "sway-value",
//     )

//     val species = speciesFor(treeType)
//     // Structure is generated once per species; growth only *reveals* it, so the
//     // branches never reshape frame-to-frame — only the wind sway moves them.
//     val skeleton = remember(treeType) {
//         buildTwig(
//             angle = -TAU / 4f,
//             lengthFrac = 0.12f,
//             widthFrac = 0.05f,
//             depth = 0,
//             s = species,
//             rng = Random(treeType * 7919 + 17),
//         )
//     }

//     Canvas(modifier = modifier) {
//         val w = size.width
//         val h = size.height

//         // A calm twilight scene so the glowing memory-leaves read well.
//         drawRect(Brush.verticalGradient(listOf(Color(0xFF15282C), Color(0xFF1E3A36))), size = size)
//         drawOval(
//             color = Color(0xFF24433B),
//             topLeft = Offset(-w * 0.25f, h * 0.86f),
//             size = Size(w * 1.5f, h * 0.32f),
//         )

//         // Overall size tracks growth: a small sapling that scales up to a full
//         // tree over the maturity cycle (and shrinks back when a new species starts).
//         // The small floors keep a brand-new tree a visible seedling, not blank.
//         val effGrowth = 0.18f + 0.82f * animGrowth
//         val scale = 0.20f + 0.80f * animGrowth

//         val base = Offset(w / 2f, h * 0.9f)
//         val tips = ArrayList<Offset>()
//         // Pass 1: branches (also collects revealed leaf anchors).
//         drawTwig(skeleton, base, effGrowth, scale, sway, h, species, tips)
//         // Pass 2: leaves on top, so the glow layers cleanly over the bark.
//         drawLeaves(tips, memoryCount, animLeaf, species, treeType, h)
//         drawFallen(animLeaf, sway, species, treeType, w, h)
//     }
// }

// // ---- Drawing ---------------------------------------------------------------

// private fun DrawScope.drawTwig(
//     twig: Twig,
//     start: Offset,
//     growth: Float,
//     scale: Float,
//     sway: Float,
//     h: Float,
//     s: Species,
//     tips: MutableList<Offset>,
// ) {
//     // Staged reveal: deeper twigs start later, so the tree visibly draws in
//     // trunk -> limbs -> canopy as growth rises.
//     val startT = (twig.depth.toFloat() / MAX_DEPTH) * 0.6f
//     val reveal = ((growth - startT) / 0.4f).coerceIn(0f, 1f)
//     if (reveal <= 0f) return

//     val swayed = twig.angle + sway * 0.04f * (twig.depth + 1) // upper twigs sway more
//     val len = twig.lengthFrac * h * reveal * scale
//     val end = Offset(start.x + cos(swayed) * len, start.y + sin(swayed) * len)

//     // A gentle curve makes the bark organic rather than a straight stick.
//     val ctrl = Offset((start.x + end.x) / 2f - sin(swayed) * len * 0.10f, (start.y + end.y) / 2f)
//     val path = Path().apply {
//         moveTo(start.x, start.y)
//         quadraticTo(ctrl.x, ctrl.y, end.x, end.y)
//     }
//     drawPath(
//         path = path,
//         brush = Brush.linearGradient(listOf(s.barkBottom, s.barkTop), start, end),
//         style = Stroke(width = (twig.widthFrac * h * scale).coerceAtLeast(2f), cap = StrokeCap.Round),
//     )

//     if (twig.isTip && reveal > 0.55f) tips.add(end)
//     twig.children.forEach { drawTwig(it, end, growth, scale, sway, h, s, tips) }
// }

// private fun DrawScope.drawLeaves(
//     tips: List<Offset>,
//     memoryCount: Int,
//     leafiness: Float,
//     s: Species,
//     treeType: Int,
//     h: Float,
// ) {
//     if (tips.isEmpty() || memoryCount <= 0 || leafiness <= 0.02f) return

//     // Canopy can't exceed what the revealed branch-tips can hold, so a young
//     // tree stays sparse and fills out only as it grows more tips.
//     val capacity = tips.size * 4
//     val target = min(min(memoryCount, LEAF_CAP), capacity)
//     val rng = Random(treeType * 9173 + 7)
//     val color = lerp(s.leafFading, s.leaf, leafiness) // brown when shedding, vivid when healthy
//     val baseR = (0.018f * h).coerceIn(4f, 11f)

//     for (i in 0 until target) {
//         val tip = tips[i % tips.size]
//         // Consume every random up front so a leaf's position/size stay fixed and
//         // only its visibility toggles as leafiness changes.
//         val ang = rng.nextFloat() * TAU
//         val rad = rng.nextFloat() * 0.045f * h
//         val roll = rng.nextFloat()
//         val sizeJ = rng.nextFloat()
//         if (roll > leafiness) continue // shedding drops a fraction of the canopy

//         val pos = Offset(tip.x + cos(ang) * rad, tip.y + sin(ang) * rad)
//         val r = baseR * (0.7f + sizeJ * 0.6f)
//         drawCircle(
//             brush = Brush.radialGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent), center = pos, radius = r * 3f),
//             radius = r * 3f,
//             center = pos,
//             blendMode = BlendMode.Screen,
//         )
//         drawCircle(color = color, radius = r, center = pos)
//         drawCircle(
//             color = Color.White.copy(alpha = 0.45f),
//             radius = r * 0.35f,
//             center = Offset(pos.x - r * 0.25f, pos.y - r * 0.25f),
//         )
//     }
// }

// private fun DrawScope.drawFallen(
//     leafiness: Float,
//     sway: Float,
//     s: Species,
//     treeType: Int,
//     w: Float,
//     h: Float,
// ) {
//     if (leafiness >= 0.9f) return
//     val count = ((1f - leafiness) * 12f).roundToInt()
//     val rng = Random(treeType * 104729 + 3)
//     repeat(count) {
//         val fx = w * (0.12f + 0.76f * rng.nextFloat())
//         val fy = h * (0.9f + 0.055f * rng.nextFloat())
//         val drift = sin(sway + rng.nextFloat() * TAU) * 2.5f
//         drawOval(
//             color = s.leafFading.copy(alpha = 0.7f),
//             topLeft = Offset(fx + drift, fy),
//             size = Size(10f, 6f),
//         )
//     }
// }
