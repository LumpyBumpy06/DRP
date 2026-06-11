package com.drp33.quietsignal.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.airbnb.lottie.value.ScaleXY
import com.drp33.quietsignal.R
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * How vibrant the tree looks, derived from the backend `deathLevel` (0..1).
 * The tree never dies — neglect only dulls the leaf colour (and, in the bird
 * stages, makes the birds fly away one by one).
 */
enum class TreeMood { THRIVING, OKAY, FADING }

fun treeMoodOf(deathLevel: Float): TreeMood = when {
    deathLevel >= 0.66f -> TreeMood.FADING
    deathLevel >= 0.33f -> TreeMood.OKAY
    else -> TreeMood.THRIVING
}

private val STAGE_ENDPOINTS = listOf(0.20f, 0.33f, 0.50f, 0.66f, 0.80f, 1.00f)

/** The fixed height of the tree content region (trunk + canopy band). Everything
 * else (hole, squirrel, leaf spawn bands) is sized relative to this, so resizing
 * the tree here scales the whole scene together. */
private val TREE_CONTENT_HEIGHT = 340.dp

/** Extra empty space reserved ABOVE the content region so the scaled-up canopy
 * isn't clipped at the top. Added to the Box height but excluded from all the
 * leaf math, which stays anchored to the bottom content region. */
private val TREE_TOP_HEADROOM = 100.dp

// ---- Bird stages (after the tree is fully grown) ---------------------------
// Past stage 5 the tree stays full-size and gains birds: stage 6 = 1 bird up to
// stage 9 = 4 birds. Each bird circles the canopy on its own randomised orbit.
private const val MAX_BIRDS = 4
private val BIRD_BASE_SIZE = TREE_CONTENT_HEIGHT * 0.188f      // ≈ 64dp at 340
private const val BIRD_ENTER_MS = 1200L   // fade/scale-in when a bird arrives
private const val BIRD_DEPART_MS = 2400L  // fly-out (spiral outward + fade) on neglect
private const val BIRD_EVENT_GAP_MS = 700L // min spacing so birds come/go one by one

// DEBUG: force this many birds at any stage so the animation can be eyeballed
// without watering 21+ times. Set back to 0 (or remove) for real behaviour.
private const val DEBUG_FORCE_BIRDS = 0

/** Birds shown for a given growth stage, before neglect thins them out. */
private fun birdsForStage(stage: Int): Int =
    if (DEBUG_FORCE_BIRDS > 0) DEBUG_FORCE_BIRDS.coerceIn(0, MAX_BIRDS)
    else (stage - (STAGE_ENDPOINTS.size - 1)).coerceIn(0, MAX_BIRDS)

// ---- Squirrel stage --------------------------------------------------------
// Stage 10: a hollow opens in the trunk and a squirrel peeks out. The squirrel
// is shy — it hides back inside when the tree is badly neglected.
private const val SQUIRREL_STAGE = 10
// All sized as fractions of the tree height so they scale with TREE_CONTENT_HEIGHT.
private val SQUIRREL_SIZE = TREE_CONTENT_HEIGHT * 0.106f       // ≈ 36dp at 340
// Hollow (dark hole) on the trunk. All relative to the tree height: Y is how far
// down the content region; X nudges from centre (negative = left) onto the trunk,
// which isn't perfectly centred at this height; W/H are the hole size.
private const val HOLE_CENTER_Y_FRAC = 0.74f
private val HOLE_OFFSET_X = TREE_CONTENT_HEIGHT * -0.012f      // ≈ -4dp
private val HOLE_WIDTH = TREE_CONTENT_HEIGHT * 0.044f          // ≈ 15dp
private val HOLE_HEIGHT = TREE_CONTENT_HEIGHT * 0.074f         // ≈ 25dp

// ---- Orange stage ----------------------------------------------------------
// Stage 11: oranges ripen in the canopy; passing birds snatch them, carry them
// off and "eat" them, after which a new orange grows elsewhere.
private const val ORANGE_STAGE = 11
private const val MAX_ORANGES = 3
private val ORANGE_SIZE = TREE_CONTENT_HEIGHT * 0.088f          // ≈ 30dp at 340
private const val ORANGE_GROW_MS = 1200L       // green→ripe scale-in
private const val ORANGE_SPAWN_GAP_MS = 2500L  // spacing between new oranges
private const val ORANGE_CARRY_MS = 2400L      // how long a bird carries before eating
// A bird must actually overlap the orange to grab it (centre-to-centre distance).
private val ORANGE_PICK_RADIUS = TREE_CONTENT_HEIGHT * 0.071f   // ≈ 24dp at 340
// Oranges only grow within the canopy blob: x is ± this fraction of content height
// from centre, y stays in the leafy band. Keeps them off the empty background.
private const val ORANGE_SPAWN_HALF_X_FRAC = 0.19f
private const val ORANGE_SPAWN_Y_TOP_FRAC = 0.30f
private const val ORANGE_SPAWN_Y_BOTTOM_FRAC = 0.44f

// DEBUG: force the squirrel / oranges on at any stage for previewing.
private const val DEBUG_FORCE_SQUIRREL = false
private const val DEBUG_FORCE_ORANGES = false

private enum class OrangeState { GROWING, RIPE, CARRIED }

/**
 * An orange on the tree. It grows in the canopy at [xPx]/[yPx] (content-space,
 * x from centre), ripens, and — once a bird passes close enough — is CARRIED by
 * that bird until eaten. Positions while carried come from the carrier's orbit.
 */
private data class Orange(
    val id: Long = Random.nextLong(),
    val xPx: Float,
    val yPx: Float,
    val spawnTimeMs: Long,
    val state: OrangeState = OrangeState.GROWING,
    val stateSinceMs: Long,
    val carrierId: Long? = null,
)

/** A new unripe orange at a random spot inside the canopy blob (fallback when no birds). */
private fun randomOrange(nowMs: Long, contentHeightPx: Float): Orange = Orange(
    xPx = (Random.nextFloat() - 0.5f) * 2f * contentHeightPx * ORANGE_SPAWN_HALF_X_FRAC,
    yPx = contentHeightPx *
        (ORANGE_SPAWN_Y_TOP_FRAC + Random.nextFloat() * (ORANGE_SPAWN_Y_BOTTOM_FRAC - ORANGE_SPAWN_Y_TOP_FRAC)),
    spawnTimeMs = nowMs,
    stateSinceMs = nowMs,
)

/**
 * A new orange placed *on* a bird's flight path, so that bird is guaranteed to
 * reach and snatch it within a lap. Biased toward the top/bottom of the orbit
 * (where x ≈ 0) so the fruit stays in the central canopy rather than the edges.
 */
private fun orangeOnBirdPath(nowMs: Long, bird: Bird): Orange {
    val base = if (Random.nextBoolean()) (PI.toFloat() / 2f) else (3f * PI.toFloat() / 2f)
    val a = base + (Random.nextFloat() - 0.5f) * 1.0f // ±0.5 rad jitter around the vertical extremes
    return Orange(
        xPx = cos(a) * bird.radiusXPx,
        yPx = bird.centerYPx + sin(a) * bird.radiusYPx,
        spawnTimeMs = nowMs,
        stateSinceMs = nowMs,
    )
}

// ---- Falling-leaf tuning (tweak these freely) ------------------------------

// Spawn rate in leaves/second. Base = when healthy; it grows as the tree wilts
// (× deathLevel) and a little with size (× stage).
private const val LEAF_RATE_HEALTHY = 0.3f
private const val LEAF_RATE_DEATH = 2.5f
private const val LEAF_RATE_STAGE = 0.08f
private const val LEAF_RATE_MAX = 4.0f

// The region leaves spawn FROM (across the bush). Y values are fractions of the
// 300dp tree box (smaller = higher up); X half-width is how far either side of
// centre a leaf can appear.

private data class LeafSpawnParams(
    val yTop: Float,
    val yBottom: Float,
    val halfWidth: Dp
)

// Leaves spawn FROM the canopy/bush band (fractions of content height) and fall to
// the floor (the trunk base). The bushes sit high in the box, so these are well
// above the floor; bigger stages have a taller, wider canopy. halfWidth spreads
// leaves across the bushes (not just by the trunk).
private val STAGE_SPAWN_PARAMS = listOf(
    LeafSpawnParams(0.43f, 0.47f, 2.dp), // Stage 0
    LeafSpawnParams(0.35f, 0.52f, 38.dp), // Stage 1
    LeafSpawnParams(0.24f, 0.50f, 50.dp), // Stage 2
    LeafSpawnParams(0.18f, 0.46f, 62.dp), // Stage 3
    LeafSpawnParams(0.14f, 0.44f, 76.dp), // Stage 4
    LeafSpawnParams(0.10f, 0.42f, 90.dp), // Stage 5
)

private data class LeafBlob(val layer: String, val group: String, val originalColor: Color)

private val LEAF_BLOBS = listOf(
    LeafBlob("Layer 5 Outlines", "Group 1", Color(0.0745f, 0.3882f, 0.2471f)),
    LeafBlob("Layer 5 Outlines", "Group 2", Color(0.0588f, 0.3176f, 0.1922f)),
    LeafBlob("Layer 4 Outlines 2", "Group 1", Color(0.0745f, 0.3882f, 0.2471f)),
    LeafBlob("Layer 4 Outlines 2", "Group 2", Color(0.0549f, 0.3176f, 0.1922f)),
    LeafBlob("Layer 4 Outlines", "Group 1", Color(0.0745f, 0.3882f, 0.2471f)),
    LeafBlob("Layer 4 Outlines", "Group 2", Color(0.0549f, 0.3176f, 0.1922f)),
    LeafBlob("Layer 3 Outlines", "Group 1", Color(0.0745f, 0.3882f, 0.2471f)),
    LeafBlob("Layer 3 Outlines", "Group 2", Color(0.0549f, 0.3176f, 0.1922f)),
    LeafBlob("Layer 2 Outlines", "Group 1", Color(0.1216f, 0.5608f, 0.3843f)),
    LeafBlob("Layer 2 Outlines", "Group 2", Color(0.0941f, 0.4784f, 0.3137f)),
    LeafBlob("Layer 12 Outlines", "Group 1", Color(0.1294f, 0.4667f, 0.302f)),
    LeafBlob("Layer 12 Outlines", "Group 2", Color(0.0588f, 0.3176f, 0.1922f)),
    LeafBlob("Layer 2 Outlines 2", "Group 1", Color(0.1216f, 0.5608f, 0.3843f)),
    LeafBlob("Layer 2 Outlines 2", "Group 2", Color(0.0941f, 0.4784f, 0.3137f)),
    LeafBlob("Layer 11 Outlines", "Group 1", Color(0.0745f, 0.3882f, 0.2471f)),
    LeafBlob("Layer 11 Outlines", "Group 2", Color(0.0549f, 0.3176f, 0.1922f)),
    LeafBlob("Layer 10 Outlines", "Group 1", Color(0.1294f, 0.4667f, 0.302f)),
    LeafBlob("Layer 10 Outlines", "Group 2", Color(0.0549f, 0.3176f, 0.1922f)),
    LeafBlob("Layer 9 Outlines", "Group 1", Color(0.1216f, 0.5608f, 0.3843f)),
    LeafBlob("Layer 9 Outlines", "Group 2", Color(0.0588f, 0.3176f, 0.1922f)),
    LeafBlob("Layer 8 Outlines", "Group 1", Color(0.1216f, 0.5608f, 0.3843f)),
    LeafBlob("Layer 8 Outlines", "Group 2", Color(0.0941f, 0.4784f, 0.3137f)),
    LeafBlob("Layer 7 Outlines", "Group 1", Color(0.051f, 0.3176f, 0.1922f)),
    LeafBlob("Layer 7 Outlines", "Group 2", Color(0.0431f, 0.2588f, 0.149f)),
    LeafBlob("Layer 16 Outlines", "Group 1", Color(0.1294f, 0.4667f, 0.302f)),
    LeafBlob("Layer 16 Outlines", "Group 2", Color(0.0588f, 0.3176f, 0.1922f)),
)

/**
 * A darker, duller version of a leaf colour for the neglected look. The tree
 * doesn't die — its leaves just lose their vibrancy and deepen toward shadow,
 * keeping each layer's relative brightness so the canopy still reads as 3D.
 */
private fun fadedLeafColor(original: Color): Color = Color(
    red = (original.red * 0.4f).coerceIn(0f, 1f),
    green = (original.green * 0.4f).coerceIn(0f, 1f),
    blue = (original.blue * 0.4f).coerceIn(0f, 1f),
)

/**
 * A bird circling the tree in one of the bird stages. Position is derived purely
 * from elapsed time + these per-bird constants, so motion is smooth and cheap.
 * `departTimeMs` is set when the bird is told to leave (neglect/lower stage); it
 * then spirals outward and fades before being removed.
 */
private data class Bird(
    val id: Long = Random.nextLong(),
    val radiusXPx: Float,
    val radiusYPx: Float,
    val centerYPx: Float,
    val angularSpeed: Float, // rad/s; sign sets orbit direction
    val phase: Float,
    val scale: Float,
    val spawnTimeMs: Long,
    val departTimeMs: Long? = null,
)

private data class BirdRender(
    val xPx: Float,
    val yPx: Float,
    val scale: Float,
    val alpha: Float,
    val faceLeft: Boolean,
    // True on the FAR half of the orbit (upper arc), where the bird should be
    // drawn behind the tree so it reads as circling around rather than across.
    val behind: Boolean,
)

/** Where this bird is right now (relative to the canopy centre), and how it's facing. */
private fun Bird.renderAt(nowMs: Long): BirdRender {
    val tSec = (nowMs - spawnTimeMs) / 1000f
    val angle = phase + angularSpeed * tSec

    var rx = radiusXPx
    var ry = radiusYPx
    var alpha = ((nowMs - spawnTimeMs).toFloat() / BIRD_ENTER_MS).coerceIn(0f, 1f)

    if (departTimeMs != null) {
        val p = ((nowMs - departTimeMs).toFloat() / BIRD_DEPART_MS).coerceIn(0f, 1f)
        rx *= 1f + p * 2.6f // spiral outward
        ry *= 1f + p * 2.6f
        alpha *= (1f - p)   // and fade out
    }

    val x = cos(angle) * rx
    val y = centerYPx + sin(angle) * ry
    // Horizontal velocity = d/dt(cos) = -sin·speed; face the way it's moving.
    val faceLeft = (-sin(angle) * angularSpeed) >= 0f
    // Upper arc (sin < 0, above the orbit centre) = far side → behind the tree.
    // Departing birds always stay in front so they're seen flying away.
    val behind = departTimeMs == null && sin(angle) < 0f
    return BirdRender(xPx = x, yPx = y, scale = scale, alpha = alpha, faceLeft = faceLeft, behind = behind)
}

/** A fresh bird on a randomised orbit around the canopy (sizes/speeds/heights vary). */
private fun randomBird(nowMs: Long, contentHeightPx: Float): Bird {
    val dir = if (Random.nextBoolean()) 1f else -1f
    return Bird(
        radiusXPx = contentHeightPx * (0.28f + Random.nextFloat() * 0.14f),
        radiusYPx = contentHeightPx * (0.09f + Random.nextFloat() * 0.08f),
        centerYPx = contentHeightPx * (0.30f + Random.nextFloat() * 0.14f),
        angularSpeed = dir * (0.5f + Random.nextFloat() * 0.5f),
        phase = Random.nextFloat() * 6.2832f,
        scale = 0.8f + Random.nextFloat() * 0.5f,
        spawnTimeMs = nowMs,
    )
}

private data class FallingLeaf(
    val id: Long = Random.nextLong(),
    val baseX: Float,      // horizontal centre the leaf sways around
    val x: Float,          // current x = baseX + sine sway
    val y: Float,
    val angle: Float,      // sprite rotation (degrees), follows the sway
    val fallSpeed: Float,  // px per second (gentle)
    val swayAmp: Float,    // px
    val swayFreq: Float,   // radians per second
    val swayPhase: Float,
    val spawnTimeMs: Long,
    val ttlMs: Long,
    val fadeOutMs: Long,
    val sizePx: Float,
    val color: Color,
    val onFloor: Boolean = false,
)

@Composable
fun WateringTree(
    stage: Int,
    deathLevel: Float,
    modifier: Modifier = Modifier,
    showFallingLeaves: Boolean = true,
    showWildlife: Boolean = true,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.tree))
    val birdComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.bird))
    val squirrelComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.squirrel))
    val orangeComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.orange))
    val death = deathLevel.coerceIn(0f, 1f)

    val idx = stage.coerceIn(0, STAGE_ENDPOINTS.lastIndex)

    // Birds for this stage, thinned out by neglect: as the tree fades, birds
    // leave one by one until none remain at full neglect.
    // showWildlife = false (forest snapshots) → no birds, squirrel or oranges,
    // just the tree itself.
    val targetBirds = if (!showWildlife) 0 else
        (birdsForStage(stage) * (1f - death)).let { kotlin.math.round(it).toInt() }.coerceIn(0, MAX_BIRDS)

    // The squirrel arrives at its stage but is shy — it ducks back into the
    // hollow when the tree is badly neglected. Oranges appear at their stage.
    val holeVisible = showWildlife && (DEBUG_FORCE_SQUIRREL || stage >= SQUIRREL_STAGE)
    val squirrelVisible = holeVisible && death < 0.5f
    val orangesActive = showWildlife && (DEBUG_FORCE_ORANGES || stage >= ORANGE_STAGE)
    val squirrelAlpha by animateFloatAsState(
        targetValue = if (squirrelVisible) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "squirrel-alpha",
    )

    val progress by animateFloatAsState(
        targetValue = STAGE_ENDPOINTS[idx],
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "tree-progress",
    )

    val zoom by animateFloatAsState(
        targetValue = when (idx) {
            0 -> 2.2f
            1 -> 1.9f
            2 -> 1.6f
            3 -> 1.3f
            // Stage 4+ all get the zoomed-out view
            else -> 0.85f
        },
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "tree-zoom",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "tree-life")

    val baseSway by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "base-sway",
    )

    val basePulse by infiniteTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "base-pulse",
    )

    // Squirrel gently bobs up and down as if peeking in and out of the hollow.
    val squirrelBob by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "squirrel-bob",
    )

    val density = LocalDensity.current
    val leaves = remember { mutableStateListOf<FallingLeaf>() }
    val birds = remember { mutableStateListOf<Bird>() }
    val oranges = remember { mutableStateListOf<Orange>() }
    var currentFrameMs by remember { mutableLongStateOf(0L) }

    val leafBitmap = ImageBitmap.imageResource(id = R.drawable.leaf)

    // deathLevel changes on every /tree poll, so keying the spawn loop on it
    // would restart the loop (resetting the accumulator) every few seconds and
    // the leaves would never accumulate. Key on Unit and read the live values.
    val currentDeath by rememberUpdatedState(death)
    val currentIdx by rememberUpdatedState(idx)
    val currentDensity by rememberUpdatedState(density)
    val currentTargetBirds by rememberUpdatedState(targetBirds)
    val currentOrangesActive by rememberUpdatedState(orangesActive)

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        var spawnAccumulator = 0f
        var lastBirdEventMs = 0L
        var lastOrangeSpawnMs = 0L

        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = frameNanos
                    return@withFrameNanos
                }

                val dt = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.033f)
                lastFrameNanos = frameNanos
                val nowMs = frameNanos / 1_000_000L
                currentFrameMs = nowMs

                val death = currentDeath
                val idx = currentIdx
                val density = currentDensity
                val params = STAGE_SPAWN_PARAMS[idx]

                val canvasHeightPx = with(density) { TREE_CONTENT_HEIGHT.toPx() }
                // Leaves rest at the trunk base. Calibrated from the rendered tree
                // (the base sits ~0.82 of content; 0.75 lands a leaf sprite's bottom
                // right at it rather than below the tree).
                val floorY = canvasHeightPx * 0.63f
                val spawnHalfWidthPx = with(density) { params.halfWidth.toPx() }
                val spawnYTop = canvasHeightPx * params.yTop
                val spawnYBottom = canvasHeightPx * params.yBottom

                // --- Birds (one-by-one arrivals/departures around the canopy) ---
                val targetBirds = currentTargetBirds
                // Drop any bird that has finished flying out.
                for (i in birds.indices.reversed()) {
                    val b = birds[i]
                    if (b.departTimeMs != null && nowMs - b.departTimeMs > BIRD_DEPART_MS) {
                        birds.removeAt(i)
                    }
                }
                val orbiting = birds.count { it.departTimeMs == null }
                if (nowMs - lastBirdEventMs >= BIRD_EVENT_GAP_MS) {
                    if (orbiting < targetBirds) {
                        birds.add(randomBird(nowMs, canvasHeightPx))
                        lastBirdEventMs = nowMs
                    } else if (orbiting > targetBirds) {
                        // Send the newest still-orbiting bird away, one at a time.
                        val out = birds.indexOfLast { it.departTimeMs == null }
                        if (out >= 0) {
                            birds[out] = birds[out].copy(departTimeMs = nowMs)
                            lastBirdEventMs = nowMs
                        }
                    }
                }

                // --- Oranges (ripen in the canopy; birds snatch them in passing) ---
                if (!currentOrangesActive) {
                    if (oranges.isNotEmpty()) oranges.clear()
                } else {
                    val pickRadius = with(density) { ORANGE_PICK_RADIUS.toPx() }
                    val busyIds = oranges.mapNotNull { it.carrierId }.toSet()

                    for (i in oranges.indices.reversed()) {
                        val o = oranges[i]
                        when (o.state) {
                            OrangeState.GROWING ->
                                if (nowMs - o.stateSinceMs > ORANGE_GROW_MS) {
                                    oranges[i] = o.copy(state = OrangeState.RIPE, stateSinceMs = nowMs)
                                }
                            OrangeState.RIPE -> {
                                // Snatch it if a free, orbiting bird passes close enough.
                                val grabber = birds.firstOrNull { b ->
                                    b.departTimeMs == null && b.id !in busyIds && run {
                                        val r = b.renderAt(nowMs)
                                        val dx = r.xPx - o.xPx
                                        val dy = r.yPx - o.yPx
                                        dx * dx + dy * dy <= pickRadius * pickRadius
                                    }
                                }
                                if (grabber != null) {
                                    oranges[i] = o.copy(
                                        state = OrangeState.CARRIED,
                                        carrierId = grabber.id,
                                        stateSinceMs = nowMs,
                                    )
                                }
                            }
                            OrangeState.CARRIED -> {
                                val carrierGone = birds.none { it.id == o.carrierId }
                                if (carrierGone || nowMs - o.stateSinceMs > ORANGE_CARRY_MS) {
                                    oranges.removeAt(i) // eaten (or carrier left)
                                }
                            }
                        }
                    }

                    if (oranges.size < MAX_ORANGES && nowMs - lastOrangeSpawnMs >= ORANGE_SPAWN_GAP_MS) {
                        // Prefer placing on a bird's path so it actually gets snatched.
                        val orbiting = birds.filter { it.departTimeMs == null }
                        oranges.add(
                            if (orbiting.isNotEmpty()) orangeOnBirdPath(nowMs, orbiting.random())
                            else randomOrange(nowMs, canvasHeightPx),
                        )
                        lastOrangeSpawnMs = nowMs
                    }
                }

                // Slow drift when healthy; speeds up as the tree fades.
                val spawnRatePerSec =
                    (LEAF_RATE_HEALTHY + death * LEAF_RATE_DEATH + idx * LEAF_RATE_STAGE)
                        .coerceAtMost(LEAF_RATE_MAX)
                spawnAccumulator += dt * spawnRatePerSec

                // Forest trees pass showFallingLeaves = false → never spawn leaves.
                val maxLeaves = if (showFallingLeaves) 70 else 0

                while (spawnAccumulator >= 1f && leaves.size < maxLeaves) {
                    spawnAccumulator -= 1f

                    // Spawn from anywhere across the bush (random x and y in the canopy band).
                    val baseX = (Random.nextFloat() - 0.5f) * 2f * spawnHalfWidthPx
                    val spawnY = spawnYTop + Random.nextFloat() * (spawnYBottom - spawnYTop)

                    val isHealthy = death < 0.4f
                    val ttl = if (isHealthy) {
                        Random.nextLong(2000L, 4000L) // Fast disappear for healthy
                    } else {
                        Random.nextLong(10_000L, 18_000L) // Longer for wilting/dead
                    }
                    val fadeOut = if (isHealthy) {
                        Random.nextLong(800L, 1500L)
                    } else {
                        Random.nextLong(1500L, 3000L)
                    }

                    val baseGreen = LEAF_BLOBS.random().originalColor
                    val leafColor = lerp(baseGreen, fadedLeafColor(baseGreen), death)

                    leaves.add(
                        FallingLeaf(
                            baseX = baseX,
                            x = baseX,
                            y = spawnY,
                            angle = 0f,
                            fallSpeed = listOf(50f, 70f, 90f, 110f, 130f).random(), // gentle px/sec
                            swayAmp = Random.nextFloat() * 25f + 15f, // 15..40 px
                            swayFreq = Random.nextFloat() * 1.5f + 1.5f, // 1.5..3.0 rad/s
                            swayPhase = Random.nextFloat() * 6.2832f,
                            spawnTimeMs = nowMs,
                            ttlMs = ttl,
                            fadeOutMs = fadeOut,
                            sizePx = Random.nextFloat() * 8f + 18f,
                            color = leafColor,
                        )
                    )
                }

                for (i in leaves.indices.reversed()) {
                    val leaf = leaves[i]
                    val ageMs = nowMs - leaf.spawnTimeMs
                    val nearDeath = death > 0.4f

                    if (leaf.onFloor) {
                        // Keep some leaves around longer, then fade them out naturally.
                        if (!nearDeath || ageMs > leaf.ttlMs) {
                            leaves.removeAt(i)
                        }
                        continue
                    }

                    // Gentle downward drift with a left/right sine sway.
                    val ageSec = ageMs / 1000f
                    val sway = sin(ageSec * leaf.swayFreq + leaf.swayPhase)
                    val newX = leaf.baseX + sway * leaf.swayAmp
                    var newY = leaf.y + leaf.fallSpeed * dt
                    val drawAngle = sway * 28f // flutter: the leaf tilts with the sway

                    if (newY >= floorY) {
                        newY = floorY
                        leaves[i] = leaf.copy(x = newX, y = newY, angle = drawAngle, onFloor = true)
                    } else {
                        leaves[i] = leaf.copy(x = newX, y = newY, angle = drawAngle)
                    }

                    // Safety cleanup for very old leaves that never settled.
                    if (ageMs > leaf.ttlMs) {
                        leaves.removeAt(i)
                    }
                }
            }
        }
    }

    val props = ArrayList<LottieDynamicProperty<*>>(LEAF_BLOBS.size * 3)
    LEAF_BLOBS.forEachIndexed { i, blob ->
        val targetColor = lerp(blob.originalColor, fadedLeafColor(blob.originalColor), death)
        val animatedColor by animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(1500, easing = FastOutSlowInEasing),
            label = "blob-${blob.layer}-${blob.group}",
        )

        props.add(
            rememberLottieDynamicProperty(
                property = LottieProperty.COLOR,
                value = animatedColor.toArgb(),
                keyPath = arrayOf("**", blob.layer, blob.group, "**"),
            ),
        )

        val individualSway = baseSway * (0.8f + ((i % 5) * 0.1f))
        props.add(
            rememberLottieDynamicProperty(
                property = LottieProperty.TRANSFORM_ROTATION,
                value = individualSway,
                keyPath = arrayOf("**", blob.layer, blob.group, "**"),
            ),
        )

        val individualPulse = basePulse * (0.995f + ((i % 3) * 0.005f))
        props.add(
            rememberLottieDynamicProperty(
                property = LottieProperty.TRANSFORM_SCALE,
                value = ScaleXY(individualPulse, individualPulse),
                keyPath = arrayOf("**", blob.layer, blob.group, "**"),
            ),
        )
    }

    val dynamicProperties = rememberLottieDynamicProperties(*props.toTypedArray())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TREE_CONTENT_HEIGHT + TREE_TOP_HEADROOM),
        contentAlignment = Alignment.BottomCenter,
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            dynamicProperties = dynamicProperties,
            modifier = Modifier
                .size(280.dp)
                .offset(y = (-8).dp)
                .graphicsLayer {
                    val f = zoom * 1.5f
                    scaleX = f
                    scaleY = f
                    transformOrigin = TransformOrigin(0.5f, 0.89f)
                },
        )

        // Squirrel peeking out of the trunk hollow (stage 10). Drawn above the
        // hole (which the leaf Canvas paints) but below the birds.
        if (squirrelAlpha > 0.01f) {
            LottieAnimation(
                composition = squirrelComposition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(SQUIRREL_SIZE)
                    .zIndex(1.6f)
                    .graphicsLayer {
                        val headroomPx = TREE_TOP_HEADROOM.toPx()
                        val holeY = headroomPx + HOLE_CENTER_Y_FRAC * TREE_CONTENT_HEIGHT.toPx()
                        translationX = HOLE_OFFSET_X.toPx()
                        // Sit so the squirrel's lower body is tucked into the hole.
                        translationY = holeY - size.height * 0.58f + squirrelBob.dp.toPx()
                        alpha = squirrelAlpha
                    },
            )
        }

        // Oranges: ripe ones sit in the canopy; carried ones ride their bird.
        oranges.forEach { orange ->
            val carrier = if (orange.state == OrangeState.CARRIED) {
                birds.firstOrNull { it.id == orange.carrierId }
            } else null
            val carrierR = carrier?.renderAt(currentFrameMs)
            val growT = ((currentFrameMs - orange.spawnTimeMs).toFloat() / ORANGE_GROW_MS)
                .coerceIn(0f, 1f)
            LottieAnimation(
                composition = orangeComposition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(ORANGE_SIZE)
                    // A carried orange shares its bird's depth (ducks behind the
                    // tree with it); fruit on the tree stays in front of the leaves.
                    .zIndex(if (carrierR?.behind == true) -1f else 1.7f)
                    .graphicsLayer {
                        val headroomPx = TREE_TOP_HEADROOM.toPx()
                        val ox: Float
                        val oy: Float
                        val scale: Float
                        if (carrierR != null) {
                            // Dangling just below the bird as it flies off.
                            ox = carrierR.xPx
                            oy = headroomPx + carrierR.yPx + size.height * 0.35f
                            scale = 0.85f
                        } else {
                            ox = orange.xPx
                            oy = headroomPx + orange.yPx
                            scale = if (orange.state == OrangeState.GROWING) 0.3f + 0.7f * growT else 1f
                        }
                        translationX = ox
                        translationY = oy - size.height / 2f
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }

        // Birds circling the canopy in the bird stages. Positions are derived from
        // the shared frame clock so they glide smoothly; each fades in on arrival
        // and spirals out on departure.
        birds.forEach { bird ->
            val r = bird.renderAt(currentFrameMs)
            LottieAnimation(
                composition = birdComposition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(BIRD_BASE_SIZE)
                    // Far arc → behind the tree (z below the tree Lottie's 0);
                    // near arc → in front of everything.
                    .zIndex(if (r.behind) -1f else 2f)
                    .graphicsLayer {
                        val headroomPx = TREE_TOP_HEADROOM.toPx()
                        translationX = r.xPx
                        translationY = headroomPx + r.yPx - size.height / 2f
                        scaleX = r.scale * if (r.faceLeft) -1f else 1f
                        scaleY = r.scale
                        alpha = r.alpha
                    },
            )
        }

        Canvas(modifier = Modifier.fillMaxSize().zIndex(1.0f)) {
            val canvasWidth = size.width
            val centerX = canvasWidth / 2f
            // Leaf positions are computed in content-region space (anchored to the
            // bottom). The Box also has headroom above that region, so shift leaf
            // drawing down by the headroom to keep them glued to the canopy.
            val headroomPx = TREE_TOP_HEADROOM.toPx()

            // The trunk hollow the squirrel lives in (stage 10+).
            if (holeVisible) {
                val holeW = HOLE_WIDTH.toPx()
                val holeH = HOLE_HEIGHT.toPx()
                val holeCx = centerX + HOLE_OFFSET_X.toPx()
                val holeCy = headroomPx + HOLE_CENTER_Y_FRAC * TREE_CONTENT_HEIGHT.toPx()
                // Carved bark rim (lit at the top), then a cavity with real depth:
                // a radial gradient that's blackest just inside the upper edge.
                drawOval(
                    color = Color(0xFF3A2615),
                    topLeft = Offset(holeCx - holeW * 0.57f, holeCy - holeH * 0.57f),
                    size = Size(holeW * 1.14f, holeH * 1.14f),
                )
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF000000), Color(0xFF120C07), Color(0xFF2A1A0C)),
                        center = Offset(holeCx, holeCy - holeH * 0.18f),
                        radius = maxOf(holeW, holeH) * 0.85f,
                    ),
                    topLeft = Offset(holeCx - holeW / 2f, holeCy - holeH / 2f),
                    size = Size(holeW, holeH),
                )
            }

            leaves.forEach { leaf ->
                val ageMs = (currentFrameMs - leaf.spawnTimeMs).coerceAtLeast(0L)
                val fadeStart = (leaf.ttlMs - leaf.fadeOutMs).coerceAtLeast(0L)
                
                val fadeT = if (ageMs < fadeStart) {
                    0f
                } else {
                    ((ageMs - fadeStart).toFloat() / leaf.fadeOutMs.toFloat()).coerceIn(0f, 1f)
                }

                val alpha = 1f - fadeT
                // Shrink from original size down to 0 during fade
                val currentSize = (leaf.sizePx * (1f - fadeT)).toInt().coerceAtLeast(0)

                if (currentSize > 0) {
                    withTransform({
                        translate(
                            left = centerX + leaf.x,
                            top = headroomPx + leaf.y
                        )
                        rotate(degrees = leaf.angle, pivot = Offset(currentSize / 2f, currentSize / 2f))
                    }) {
                        drawImage(
                            image = leafBitmap,
                            dstSize = IntSize(currentSize, currentSize),
                            alpha = alpha,
                            colorFilter = ColorFilter.tint(leaf.color)
                        )
                    }
                }
            }
        }
    }
}

/** A round "water the tree" button mirroring the mic button. */
@Composable
fun WaterButton(onWater: () -> Unit, size: Dp = 120.dp, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onWater,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6)),
            modifier = Modifier.size(size),
        ) {
            Text(text = "💧", fontSize = 40.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Water", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Short prompt under the tree — nudges keeping in touch as the colour fades. */
fun treeHint(mood: TreeMood): String = when (mood) {
    TreeMood.FADING -> "🍃 The leaves are fading — share a moment to bring their colour back"
    TreeMood.OKAY -> "🌿 Keep in touch to keep the leaves bright"
    TreeMood.THRIVING -> "🌳 Your tree is thriving — keep it up!"
}
