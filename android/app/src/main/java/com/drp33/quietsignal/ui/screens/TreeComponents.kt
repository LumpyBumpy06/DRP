package com.drp33.quietsignal.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.airbnb.lottie.value.ScaleXY
import com.drp33.quietsignal.R
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.random.Random

/** How dead the tree looks, derived from the backend `deathLevel` (0..1). */
enum class DeathState { HEALTHY, WILTING, DYING, DEAD }

fun deathStateOf(deathLevel: Float): DeathState = when {
    deathLevel >= 1.0f -> DeathState.DEAD
    deathLevel >= 0.8f -> DeathState.DYING
    deathLevel >= 0.4f -> DeathState.WILTING
    else -> DeathState.HEALTHY
}

private val STAGE_ENDPOINTS = listOf(0.20f, 0.33f, 0.50f, 0.66f, 0.80f, 1.00f)

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

private val STAGE_SPAWN_PARAMS = listOf(
    LeafSpawnParams(0.85f, 0.98f, 20.dp), // Stage 0
    LeafSpawnParams(0.75f, 0.98f, 30.dp), // Stage 1
    LeafSpawnParams(0.65f, 0.98f, 40.dp), // Stage 2
    LeafSpawnParams(0.55f, 0.75f, 50.dp), // Stage 3
    LeafSpawnParams(0.55f, 0.75f, 60.dp), // Stage 4
    LeafSpawnParams(0.55f, 0.75f, 70.dp), // Stage 5
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
 * Calculates a dried-leaf brown version of a green color while preserving its
 * relative brightness and contrast to maintain the illusion of depth.
 */
private fun getDeadColor(original: Color): Color {
    // Dried-leaf brown base: approximately Rosy Brown (0xFFBC8F8F)
    val brightness = (original.red + original.green + original.blue) / 3f
    return Color(
        red = ((0.74f * brightness) + 0.1f).coerceIn(0f, 1f),
        green = (0.56f * brightness).coerceIn(0f, 1f),
        blue = (0.56f * brightness).coerceIn(0f, 1f),
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
    val onFloor: Boolean = false,
)

@Composable
fun WateringTree(stage: Int, deathLevel: Float, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.tree))
    val death = deathLevel.coerceIn(0f, 1f)

    val idx = stage.coerceIn(0, STAGE_ENDPOINTS.lastIndex)

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
            4 -> 1.1f
            else -> 1.0f
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

    val density = LocalDensity.current
    val leaves = remember { mutableStateListOf<FallingLeaf>() }
    var currentFrameMs by remember { mutableLongStateOf(0L) }

    val leafBitmap = ImageBitmap.imageResource(id = R.drawable.leaf)

    // deathLevel changes on every /tree poll, so keying the spawn loop on it
    // would restart the loop (resetting the accumulator) every few seconds and
    // the leaves would never accumulate. Key on Unit and read the live values.
    val currentDeath by rememberUpdatedState(death)
    val currentIdx by rememberUpdatedState(idx)
    val currentDensity by rememberUpdatedState(density)

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        var spawnAccumulator = 0f

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

                val canvasHeightPx = with(density) { 300.dp.toPx() }
                val floorY = canvasHeightPx * 0.95f
                val spawnHalfWidthPx = with(density) { params.halfWidth.toPx() }
                val spawnYTop = canvasHeightPx * params.yTop
                val spawnYBottom = canvasHeightPx * params.yBottom

                if (death >= 1.0f) {
                    leaves.clear()
                    spawnAccumulator = 0f
                    return@withFrameNanos
                }

                // Slow drift when healthy; speeds up as the tree wilts.
                val spawnRatePerSec =
                    (LEAF_RATE_HEALTHY + death * LEAF_RATE_DEATH + idx * LEAF_RATE_STAGE)
                        .coerceAtMost(LEAF_RATE_MAX)
                spawnAccumulator += dt * spawnRatePerSec

                val maxLeaves = 70

                while (spawnAccumulator >= 1f && leaves.size < maxLeaves) {
                    spawnAccumulator -= 1f

                    // Spawn from anywhere across the bush (random x and y in the canopy band).
                    val baseX = (Random.nextFloat() - 0.5f) * 2f * spawnHalfWidthPx
                    val spawnY = spawnYTop + Random.nextFloat() * (spawnYBottom - spawnYTop)

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
                            ttlMs = Random.nextLong(10_000L, 18_000L),
                            fadeOutMs = Random.nextLong(1500L, 3000L),
                            sizePx = Random.nextFloat() * 8f + 18f,
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

    val props = ArrayList<LottieDynamicProperty<*>>(LEAF_BLOBS.size * 4)
    LEAF_BLOBS.forEachIndexed { i, blob ->
        val targetColor = lerp(blob.originalColor, getDeadColor(blob.originalColor), death)
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

        val leafOpacity = if (death >= 0.95f) 0 else 100
        val animatedOpacity by animateIntAsState(
            targetValue = leafOpacity,
            animationSpec = tween(1500, easing = FastOutSlowInEasing),
            label = "opacity-${blob.layer}-${blob.group}",
        )

        props.add(
            rememberLottieDynamicProperty(
                property = LottieProperty.OPACITY,
                value = animatedOpacity,
                keyPath = arrayOf("**", blob.layer, blob.group, "**"),
            ),
        )
    }

    val dynamicProperties = rememberLottieDynamicProperties(*props.toTypedArray())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clipToBounds(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            dynamicProperties = dynamicProperties,
            modifier = Modifier
                .size(220.dp)
                .offset(y = 24.dp)
                .graphicsLayer {
                    val f = zoom * 1.9f
                    scaleX = f
                    scaleY = f
                    transformOrigin = TransformOrigin(0.5f, 0.89f)
                },
        )

        Canvas(modifier = Modifier.fillMaxSize().zIndex(1.0f)) {
            val canvasWidth = size.width
            val centerX = canvasWidth / 2f

            leaves.forEach { leaf ->
                val ageMs = (currentFrameMs - leaf.spawnTimeMs).coerceAtLeast(0L)
                val alpha = when {
                    leaf.onFloor -> 1f
                    ageMs < leaf.ttlMs - leaf.fadeOutMs -> 1f
                    else -> {
                        val t = ((ageMs - (leaf.ttlMs - leaf.fadeOutMs)).toFloat() / leaf.fadeOutMs.toFloat())
                            .coerceIn(0f, 1f)
                        1f - t
                    }
                }

                withTransform({
                    translate(
                        left = centerX + leaf.x,
                        top = leaf.y
                    )
                    // Rotate around the leaf itself — the default pivot is the
                    // canvas centre, which was flinging leaves off-screen.
                    rotate(degrees = leaf.angle, pivot = Offset(12f, 12f))
                }) {
                    drawImage(
                        image = leafBitmap,
                        dstSize = IntSize(24, 24),
                        alpha = alpha
                    )
                }
            }
        }
    }
}

/** A round "water the tree" button mirroring the mic button. */
@Composable
fun WaterButton(onWater: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onWater,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6)),
            modifier = Modifier.size(120.dp),
        ) {
            Text(text = "💧", fontSize = 44.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Water the tree", style = MaterialTheme.typography.titleMedium)
    }
}

/** Short prompt under the tree — nudges watering, escalating as it wilts. */
fun treeHint(death: DeathState): String = when (death) {
    DeathState.DEAD -> "🥀 The tree has wilted — water it together to revive it!"
    DeathState.DYING -> "😟 Your tree is dying — water it now!"
    DeathState.WILTING -> "🍂 It's getting thirsty — give it some water"
    DeathState.HEALTHY -> "💧 Water the tree together to help it grow"
}
