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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.LongState
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
import com.airbnb.lottie.LottieComposition
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

private val STAGE_ENDPOINTS = listOf(
    0.20f, 0.33f, 0.50f, 0.66f, 0.80f, 1.00f, // Stages 0-5 (growing)
    1.00f, 1.00f, 1.00f, 1.00f                // Stages 6-9 (fully grown + birds)
)

// The stage at which the tree reaches full size; later stages only add birds.
private const val FULL_GROWTH_STAGE = 5

/** The fixed height of the tree content region (trunk + canopy band). Everything
 * else (leaf spawn bands, birds) is sized relative to this, so resizing
 * the tree here scales the whole scene together. */
private val TREE_CONTENT_HEIGHT = 340.dp

/** Extra empty space reserved ABOVE the content region so the scaled-up canopy
 * isn't clipped at the top. Added to the Box height but excluded from all the
 * leaf math, which stays anchored to the bottom content region. */
private val TREE_TOP_HEADROOM = 100.dp

// ---- Bird stages (after the tree is fully grown) ---------------------------
private const val MAX_BIRDS = 4
private val BIRD_BASE_SIZE = TREE_CONTENT_HEIGHT * 0.188f
private const val BIRD_ENTER_MS = 1200L
private const val BIRD_DEPART_MS = 2400L
private const val BIRD_EVENT_GAP_MS = 700L

private const val DEBUG_FORCE_BIRDS = 0

private fun birdsForStage(stage: Int): Int =
    if (DEBUG_FORCE_BIRDS > 0) DEBUG_FORCE_BIRDS.coerceIn(0, MAX_BIRDS)
    else (stage - FULL_GROWTH_STAGE).coerceIn(0, MAX_BIRDS)

// ---- Falling-leaf tuning ----
private const val LEAF_RATE_HEALTHY = 0.3f
private const val LEAF_RATE_DEATH = 2.5f
private const val LEAF_RATE_STAGE = 0.08f
private const val LEAF_RATE_MAX = 4.0f

private data class LeafSpawnParams(
    val yTop: Float,
    val yBottom: Float,
    val halfWidth: Dp
)

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

private fun fadedLeafColor(original: Color): Color = Color(
    red = (original.red * 0.4f).coerceIn(0f, 1f),
    green = (original.green * 0.4f).coerceIn(0f, 1f),
    blue = (original.blue * 0.4f).coerceIn(0f, 1f),
)

private data class Bird(
    val id: Long = Random.nextLong(),
    val radiusXPx: Float,
    val radiusYPx: Float,
    val centerYPx: Float,
    val angularSpeed: Float,
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
    val behind: Boolean,
)

private fun Bird.renderAt(nowMs: Long): BirdRender {
    val tSec = (nowMs - spawnTimeMs) / 1000f
    val angle = phase + angularSpeed * tSec

    var rx = radiusXPx
    var ry = radiusYPx
    var alpha = ((nowMs - spawnTimeMs).toFloat() / BIRD_ENTER_MS).coerceIn(0f, 1f)

    if (departTimeMs != null) {
        val p = ((nowMs - departTimeMs).toFloat() / BIRD_DEPART_MS).coerceIn(0f, 1f)
        rx *= 1f + p * 2.6f
        ry *= 1f + p * 2.6f
        alpha *= (1f - p)
    }

    val x = cos(angle) * rx
    val y = centerYPx + sin(angle) * ry
    val faceLeft = (-sin(angle) * angularSpeed) >= 0f
    val behind = departTimeMs == null && sin(angle) < 0f
    return BirdRender(xPx = x, yPx = y, scale = scale, alpha = alpha, faceLeft = faceLeft, behind = behind)
}

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
    val baseX: Float,
    val spawnY: Float,
    val fallSpeed: Float,
    val swayAmp: Float,
    val swayFreq: Float,
    val swayPhase: Float,
    val spawnTimeMs: Long,
    val ttlMs: Long,
    val fadeOutMs: Long,
    val sizePx: Float,
    val color: Color,
    var floorY: Float? = null,
    var landTimeMs: Long? = null,
)

@Composable
fun WateringTree(
    stage: Int,
    deathLevel: Float,
    modifier: Modifier = Modifier,
    showFallingLeaves: Boolean = true,
    showWildlife: Boolean = true,
) {
    val compositionResult = rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.tree))
    val composition = compositionResult.value
    val birdCompositionResult = rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.bird))
    val birdComposition = birdCompositionResult.value
    
    val death = deathLevel.coerceIn(0f, 1f)
    val idx = stage.coerceIn(0, STAGE_ENDPOINTS.lastIndex)

    val targetBirds = if (!showWildlife) 0 else
        (birdsForStage(stage) * (1f - death)).let { kotlin.math.round(it).toInt() }.coerceIn(0, MAX_BIRDS)

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

    val density = LocalDensity.current
    val leaves = remember { mutableStateListOf<FallingLeaf>() }
    val birds = remember { mutableStateListOf<Bird>() }
    val currentFrameMs = remember { mutableLongStateOf(0L) }

    val leafBitmap = ImageBitmap.imageResource(id = R.drawable.leaf)

    val currentDeath by rememberUpdatedState(death)
    val currentIdx by rememberUpdatedState(idx)
    val currentDensity by rememberUpdatedState(density)
    val currentTargetBirds by rememberUpdatedState(targetBirds)

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        var spawnAccumulator = 0f
        var lastBirdEventMs = 0L

        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = frameNanos
                    return@withFrameNanos
                }

                val dt = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.033f)
                lastFrameNanos = frameNanos
                val nowMs = frameNanos / 1_000_000L
                currentFrameMs.longValue = nowMs

                val dVal = currentDeath
                val iVal = currentIdx
                val densVal = currentDensity
                val params = STAGE_SPAWN_PARAMS[iVal.coerceAtMost(STAGE_SPAWN_PARAMS.lastIndex)]

                val canvasHeightPx = densVal.run { TREE_CONTENT_HEIGHT.toPx() }
                val floorY = canvasHeightPx * 0.63f
                val spawnHalfWidthPx = densVal.run { params.halfWidth.toPx() }
                val spawnYTop = canvasHeightPx * params.yTop
                val spawnYBottom = canvasHeightPx * params.yBottom

                // --- Birds ---
                val tBirdsVal = currentTargetBirds
                for (i in birds.indices.reversed()) {
                    val b = birds[i]
                    if (b.departTimeMs != null && nowMs - b.departTimeMs > BIRD_DEPART_MS) {
                        birds.removeAt(i)
                    }
                }
                val orbiting = birds.count { it.departTimeMs == null }
                if (nowMs - lastBirdEventMs >= BIRD_EVENT_GAP_MS) {
                    if (orbiting < tBirdsVal) {
                        birds.add(randomBird(nowMs, canvasHeightPx))
                        lastBirdEventMs = nowMs
                    } else if (orbiting > tBirdsVal) {
                        val out = birds.indexOfLast { it.departTimeMs == null }
                        if (out >= 0) {
                            birds[out] = birds[out].copy(departTimeMs = nowMs)
                            lastBirdEventMs = nowMs
                        }
                    }
                }

                // --- Leaves ---
                if (!showFallingLeaves) {
                    leaves.clear()
                } else {
                    val baseRate = LEAF_RATE_HEALTHY + (LEAF_RATE_DEATH - LEAF_RATE_HEALTHY) * dVal
                    val stageBoost = iVal * LEAF_RATE_STAGE
                    val rate = (baseRate + stageBoost).coerceAtMost(LEAF_RATE_MAX)

                    spawnAccumulator += rate * dt
                    val maxLeaves = 70
                    while (spawnAccumulator >= 1f && leaves.size < maxLeaves) {
                        spawnAccumulator -= 1f
                        val baseX = (Random.nextFloat() - 0.5f) * 2f * spawnHalfWidthPx
                        val spawnY = spawnYTop + Random.nextFloat() * (spawnYBottom - spawnYTop)
                        
                        val isHealthy = dVal < 0.4f
                        val ttl = if (isHealthy) Random.nextLong(2000L, 4000L) else Random.nextLong(10_000L, 18_000L)
                        val fadeOut = if (isHealthy) Random.nextLong(800L, 1500L) else Random.nextLong(1500L, 3000L)

                        val baseGreen = LEAF_BLOBS.random().originalColor
                        val leafColor = lerp(baseGreen, fadedLeafColor(baseGreen), dVal)

                        leaves.add(
                            FallingLeaf(
                                baseX = baseX,
                                spawnY = spawnY,
                                fallSpeed = listOf(50f, 70f, 90f, 110f, 130f).random(),
                                swayAmp = Random.nextFloat() * 25f + 15f,
                                swayFreq = Random.nextFloat() * 1.5f + 1.5f,
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

                        if (leaf.landTimeMs != null) {
                            if (dVal <= 0.4f || ageMs > leaf.ttlMs) {
                                leaves.removeAt(i)
                            }
                        } else if (ageMs > leaf.ttlMs) {
                            leaves.removeAt(i)
                        } else {
                            val ageSec = ageMs / 1000f
                            val currentY = leaf.spawnY + leaf.fallSpeed * ageSec
                            if (currentY >= floorY) {
                                leaf.floorY = floorY
                                leaf.landTimeMs = nowMs
                            }
                        }
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
            .height(TREE_CONTENT_HEIGHT + TREE_TOP_HEADROOM)
            .graphicsLayer { clip = false },
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
                    clip = false
                },
        )

        birds.forEach { bird ->
            BirdRenderer(bird, birdComposition, currentFrameMs)
        }

        Canvas(modifier = Modifier.fillMaxSize().zIndex(1.0f)) {
            val canvasWidth = size.width
            val centerX = canvasWidth / 2f
            val headroomPx = TREE_TOP_HEADROOM.toPx()

            leaves.forEach { leaf ->
                val now = currentFrameMs.longValue
                val ageMs = (now - leaf.spawnTimeMs).coerceAtLeast(0L)
                val fadeStart = (leaf.ttlMs - leaf.fadeOutMs).coerceAtLeast(0L)
                
                val fadeT = if (ageMs < fadeStart) {
                    0f
                } else {
                    ((ageMs - fadeStart).toFloat() / leaf.fadeOutMs.toFloat()).coerceIn(0f, 1f)
                }

                val alpha = 1f - fadeT
                val currentSize = (leaf.sizePx * (1f - fadeT)).toInt().coerceAtLeast(0)

                if (currentSize > 0) {
                    val ageSec = ageMs / 1000f
                    val sway = sin(ageSec * leaf.swayFreq + leaf.swayPhase)
                    val leafX = leaf.baseX + sway * leaf.swayAmp
                    
                    val leafY = if (leaf.landTimeMs != null) {
                        leaf.floorY ?: 0f
                    } else {
                        leaf.spawnY + leaf.fallSpeed * ageSec
                    }
                    
                    val drawAngle = if (leaf.landTimeMs != null) 0f else sway * 28f

                    withTransform({
                        translate(
                            left = centerX + leafX,
                            top = headroomPx + leafY
                        )
                        rotate(degrees = drawAngle, pivot = Offset(currentSize / 2f, currentSize / 2f))
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

@Composable
private fun BoxScope.BirdRenderer(bird: Bird, birdComposition: LottieComposition?, currentFrameMs: LongState) {
    val r = bird.renderAt(currentFrameMs.longValue)
    LottieAnimation(
        composition = birdComposition,
        iterations = LottieConstants.IterateForever,
        modifier = Modifier
            .size(BIRD_BASE_SIZE)
            .align(Alignment.TopCenter)
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
