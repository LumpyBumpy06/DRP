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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
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
    val x: Float,
    val y: Float,
    val angle: Float,
    val angleDtMs: Long,
    val lastAngleUpdateMs: Long,
    val gravity: Float,
    val sideSpeed: Float,
    val gustFactor: Float,
    val spawnTimeMs: Long,
    val ttlMs: Long,
    val fadeOutMs: Long,
    val sizePx: Float,
    val onFloor: Boolean = false,
)

private fun clampAngleToDownwardCone(angle: Float): Float {
    var a = angle % 360f
    if (a < 0f) a += 360f

    // Keep the leaf moving generally downward.
    // 90deg = down, so limit it to a cone around that.
    return a.coerceIn(55f, 125f)
}

private fun randomLeafAngle(): Float {
    // Around 90 degrees means downward in screen coordinates.
    return (85f + (Random.nextFloat() * 10f)) // 85..95
}

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
    val canvasHeightPx = with(density) { 300.dp.toPx() }
    val floorY = canvasHeightPx * 0.90f

    val leaves = remember { mutableStateListOf<FallingLeaf>() }
    var currentFrameMs by remember { mutableLongStateOf(0L) }

    val leafBitmap = ImageBitmap.imageResource(id = R.drawable.leaf)

    LaunchedEffect(death, stage) {
        if (death >= 1.0f) {
            leaves.clear()
            return@LaunchedEffect
        }

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

                // Lower death => fewer leaves. Higher death => more leaves.
                val spawnRatePerSec = (0.25f + death * 2.2f + idx * 0.12f).coerceAtMost(4.0f)
                spawnAccumulator += dt * spawnRatePerSec

                val maxLeaves = 70

                while (spawnAccumulator >= 1f && leaves.size < maxLeaves) {
                    spawnAccumulator -= 1f

                    val spawnXRange = 100f + (idx * 20f)
                    val x = (Random.nextFloat() - 0.5f) * spawnXRange

                    leaves.add(
                        FallingLeaf(
                            x = x,
                            y = canvasHeightPx * 0.40f,
                            angle = randomLeafAngle(),
                            angleDtMs = Random.nextLong(250L, 451L),
                            lastAngleUpdateMs = nowMs,
                            gravity = listOf(35f, 40f, 45f, 50f, 60f, 65f, 70f, 80f).random(),
                            sideSpeed = Random.nextFloat() * 18f + 4f,
                            gustFactor = Random.nextFloat() * 1.2f + 0.6f,
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

                    // Occasionally perturb angle, but keep it in a downward cone.
                    val updatedAngle = if (nowMs - leaf.lastAngleUpdateMs > leaf.angleDtMs) {
                        val delta = Random.nextInt(-18, 19).toFloat()
                        clampAngleToDownwardCone(leaf.angle + delta)
                    } else {
                        leaf.angle
                    }

                    val angleRad = Math.toRadians(updatedAngle.toDouble()).toFloat()

                    // Screen coords: x right, y down.
                    // Use a downward-biased movement like your pygame version.
                    val gust = baseSway * 0.12f
                    val vx = kotlin.math.cos(angleRad) * leaf.sideSpeed + gust * -0.35f
    val vy = sin(angleRad) * leaf.gravity

    val newX = leaf.x + (vx * dt * 60f)
    var newY = leaf.y + (vy * dt * 60f)

                    // Never go upward.
                    if (newY < leaf.y) {
                        newY = leaf.y + (leaf.gravity * dt * 0.9f)
                    }

                    if (newY >= floorY) {
                        newY = floorY
                        leaves[i] = leaf.copy(
                            x = newX,
                            y = newY,
                            angle = updatedAngle,
                            lastAngleUpdateMs = nowMs,
                            onFloor = true,
                        )
                    } else {
                        // Add a small random wobble sometimes, but never enough to go upward.
                        leaves[i] = leaf.copy(
                            x = newX,
                            y = newY,
                            angle = updatedAngle,
                            lastAngleUpdateMs = nowMs,
                        )
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
                    rotate(degrees = leaf.angle)
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
