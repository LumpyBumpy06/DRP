package com.drp33.quietsignal.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.drp33.quietsignal.R

/** How dead the tree looks, derived from the backend `deathLevel` (0..1). */
enum class DeathState { HEALTHY, WILTING, DYING, DEAD }

fun deathStateOf(deathLevel: Float): DeathState = when {
    deathLevel >= 1.0f -> DeathState.DEAD
    deathLevel >= 0.8f -> DeathState.DYING
    deathLevel >= 0.4f -> DeathState.WILTING
    else -> DeathState.HEALTHY
}

private val HEALTHY_LEAF = Color(0xFF4CAF50)
private val DEAD_LEAF = Color(0xFFBC8F8F) // dried-leaf brown, distinct from the trunk

// The green leaf-cluster layers in tree.json (the trunk/branch layers are left brown).
private val LEAF_LAYERS = listOf(
    "Layer 5 Outlines", "Layer 4 Outlines", "Layer 4 Outlines 2", "Layer 3 Outlines",
    "Layer 2 Outlines", "Layer 2 Outlines 2", "Layer 12 Outlines", "Layer 8 Outlines",
    "Layer 16 Outlines", "Layer 11 Outlines", "Layer 10 Outlines", "Layer 9 Outlines",
    "Layer 7 Outlines",
)

private val STAGE_ENDPOINTS = listOf(0.20f, 0.33f, 0.50f, 0.66f, 0.80f, 1.00f)

/**
 * The shared tree. [stage] (backend growth) sets the Lottie progress; [deathLevel]
 * wilts it: leaves dry to brown, the whole tree shrinks, and every 0.4 of death
 * regresses the growth stage by one (so neglect literally un-grows the tree).
 */
@Composable
fun WateringTree(stage: Int, deathLevel: Float, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.tree))
    val death = deathLevel.coerceIn(0f, 1f)

    // Regress the growth stage: −1 stage for every 0.4 of death.
    val regress = (death / 0.4f).toInt()
    val idx = (stage - regress).coerceIn(0, STAGE_ENDPOINTS.lastIndex)

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
    // Shrink as it dies.
    val wiltScale by animateFloatAsState(
        targetValue = 1f - 0.3f * death,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "tree-wilt",
    )
    // Leaves dry from green to brown.
    val leafColor by animateColorAsState(
        targetValue = lerp(HEALTHY_LEAF, DEAD_LEAF, death),
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "leaf-color",
    )

    val leafArgb = leafColor.toArgb()
    val props = ArrayList<LottieDynamicProperty<Int>>(LEAF_LAYERS.size)
    for (layer in LEAF_LAYERS) {
        props.add(
            rememberLottieDynamicProperty(
                property = LottieProperty.COLOR,
                value = leafArgb,
                keyPath = arrayOf(layer, "**"),
            ),
        )
    }
    val dynamicProperties = rememberLottieDynamicProperties(*props.toTypedArray())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
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
                    val f = zoom * 1.9f * wiltScale
                    scaleX = f
                    scaleY = f
                    transformOrigin = TransformOrigin(0.5f, 0.89f)
                },
        )
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

/** Short prompt under the tree — nudges watering, escalating as it wilts. */
fun treeHint(death: DeathState): String = when (death) {
    DeathState.DEAD -> "🥀 The tree has wilted — water it together to revive it!"
    DeathState.DYING -> "😟 Your tree is dying — water it now!"
    DeathState.WILTING -> "🍂 It's getting thirsty — give it some water"
    DeathState.HEALTHY -> "💧 Water the tree together to help it grow"
}
