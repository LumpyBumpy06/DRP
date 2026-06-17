package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.drp33.quietsignal.R
import com.drp33.quietsignal.model.ForestWeek
import com.drp33.quietsignal.model.WEEK_SECONDS
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.Newsreader
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A smooth wave hill that starts and ends at the baseline with zero slope
 * for seamless tiling. Closed down to [bottom]. */
private fun smoothHill(x1: Float, baseY: Float, amp: Float, wavelength: Float, bottom: Float): Path =
    Path().apply {
        moveTo(0f, baseY)
        val step = 10f
        var x = 0f
        while (x < x1) {
            x = (x + step).coerceAtMost(x1)
            val phase = x / wavelength
            val y = baseY - amp * (0.5f - 0.5f * cos(2f * PI.toFloat() * phase))
            lineTo(x, y)
        }
        lineTo(x1, bottom)
        lineTo(0f, bottom)
        close()
    }

/** Label from a week-start epoch (seconds). Includes the time so the short test
 * "weeks" (60s apart) stay visibly distinct. */
private fun weekLabel(weekStart: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(weekStart * 1000))

/**
 * The shared forest as a Grove tab with a 2.5D parallax feel: drifting clouds,
 * two layers of hills that slide as you swipe, and trees that alternate
 * near (large, low, in front) / far (small, high, faded) for depth. Tap a tree
 * to relive that week as a [Montage].
 */
// hello world 
@Composable
fun ForestPane(vm: MemoriesViewModel, contentPadding: PaddingValues = PaddingValues()) {
    LaunchedEffect(Unit) {
        while (true) {
            vm.load()
            vm.loadForest()
            val periodMs = WEEK_SECONDS * 1000
            val nowMs = System.currentTimeMillis()
            delay(periodMs - (nowMs % periodMs) + 300)
        }
    }

    // The memories a frozen tree holds are those shared in its captured window
    // [periodStart, periodEnd). Legacy trees (added before period ranges existed)
    // fall back to the old time-bucket match so they still show something.
    val memoriesFor = remember(vm.memories) {
        { week: ForestWeek ->
            if (week.periodEnd > 0L) {
                vm.memories.filter { it.epoch >= week.periodStart && it.epoch < week.periodEnd }
            } else {
                vm.memories.filter { (it.epoch / WEEK_SECONDS) * WEEK_SECONDS == week.weekStart }
            }
        }
    }
    // Tapping a tree opens that week's gallery; the gallery offers "Play montage".
    var galleryWeek by remember { mutableStateOf<ForestWeek?>(null) }
    var montageWeek by remember { mutableStateOf<ForestWeek?>(null) }

    // The backend only ever returns FROZEN (elapsed) weeks — the live tree
    // joins the forest automatically once its week ends.
    val weeks = vm.forestWeeks

    // Scroll position (px) drives the parallax. Items are uniform width, so
    // index*width + offset is an exact scroll measure — and reading it from the
    // LazyListState keeps us lazy (only visible trees compose/animate).
    val listState = rememberLazyListState()
    val itemWidthPx = with(LocalDensity.current) { 156.dp.toPx() }
    val scrollPx by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex * itemWidthPx + listState.firstVisibleItemScrollOffset
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ---- parallax backdrop: clouds (slow) + far hills + mid hills (faster) ----
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cloud = Grove.Surface

            // Clouds: Tiled every 1.5 screen widths
            val cloudParallax = 0.08f
            val cloudSpacing = w * 1.5f
            val cloudOffset = (scrollPx * cloudParallax) % cloudSpacing
            
            for (tile in -1..2) {
                val tileX = tile * cloudSpacing - cloudOffset
                val cy = h * 0.14f
                val xs = floatArrayOf(0.16f, 0.52f, 0.86f)
                xs.forEachIndexed { i, fx ->
                    val cx = tileX + w * fx
                    val rx = if (i % 2 == 0) 92f else 66f
                    drawOval(
                        color = cloud.copy(alpha = 0.55f),
                        topLeft = Offset(cx, cy + (i % 3) * 26f),
                        size = Size(rx * 2f, rx * 0.62f),
                    )
                    drawOval(
                        color = cloud.copy(alpha = 0.45f),
                        topLeft = Offset(cx + rx * 0.5f, cy - 10f + (i % 3) * 26f),
                        size = Size(rx * 1.4f, rx * 0.5f),
                    )
                }
            }

            // Far Hills: Truly seamless tiling
            val farParallax = 0.18f
            val farSpacing = w * 2.2f 
            val farOffset = (scrollPx * farParallax) % farSpacing
            for (tile in -1..1) {
                translate(left = tile * farSpacing - farOffset) {
                    drawPath(
                        smoothHill(farSpacing, h * 0.56f, h * 0.085f, farSpacing / 3f, h),
                        Grove.FoliageRest.copy(alpha = 0.32f),
                    )
                }
            }

            // Mid Hills: Truly seamless tiling
            val midParallax = 0.32f
            val midSpacing = w * 1.8f
            val midOffset = (scrollPx * midParallax) % midSpacing
            for (tile in -1..1) {
                translate(left = tile * midSpacing - midOffset) {
                    drawPath(
                        smoothHill(midSpacing, h * 0.65f, h * 0.07f, midSpacing / 2f, h),
                        Grove.Foliage2.copy(alpha = 0.22f),
                    )
                }
            }
        }

        // Ground band at the bottom (above the nav bar).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(160.dp)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to Grove.Ground)),
        )

        // A few birds drifting across the sky up top — purely for life. They stay
        // in the upper band and parallax with the scroll like the rest of the sky.
        ForestBirds(scrollPx = scrollPx)

        if (weeks.isEmpty()) {
            Text(
                text = "🌱 Your forest is just beginning.\nShare moments to grow this month's tree.",
                color = Grove.InkSoft,
                fontFamily = NunitoSans,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(40.dp),
            )
        } else {
            // Newest week first so the latest tree greets you on the left.
            val display = weeks.asReversed()
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxSize().graphicsLayer { clip = false },
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 136.dp, top = 116.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                itemsIndexed(display, key = { _, w -> w.weekStart }) { index, week ->
                    val mems = memoriesFor(week)
                    val far = index % 2 == 1
                    Column(
                        modifier = Modifier
                            .width(156.dp)
                            .wrapContentWidth(unbounded = true)
                            .zIndex(if (far) 0f else 1f)
                            .graphicsLayer {
                                // Lift the entire column (tree + labels) for far trees
                                translationY = if (far) (-24).dp.toPx() else 8.dp.toPx()
                                clip = false
                            }
                            .clickable { galleryWeek = week },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(unbounded = true) // Allow tree branches to bleed outside 156dp
                                .graphicsLayer {
                                    // Scale ONLY the tree graphic
                                    val s = if (far) 0.66f else 0.94f
                                    scaleX = s
                                    scaleY = s
                                    alpha = if (far) 0.88f else 1f
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                    clip = false // Ensure branches aren't clipped by this box
                                },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            // Forest snapshots are just the tree — no falling
                            // leaves and no birds/squirrel/oranges.
                            // We wrap in remember to avoid redundant Lottie re-composition
                            // while scrolling a long list.
                            val stage = week.stage
                            val deathLevel = week.deathLevel
                            remember(stage, deathLevel) {
                                @Composable {
                                    WateringTree(
                                        stage = stage,
                                        deathLevel = deathLevel,
                                        showFallingLeaves = false,
                                        showWildlife = false,
                                    )
                                }
                            }.invoke()
                        }

                        // Pulled up to sit close under the trunk (the tree box has
                        // empty ground space below the trunk we don't want to show).
                        Column(
                            modifier = Modifier.offset(y = (-26).dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Month ${week.weekIndex}",
                                fontFamily = Newsreader,
                                fontWeight = FontWeight.Medium,
                                color = Grove.Ink,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = weekLabel(week.weekStart),
                                fontFamily = NunitoSans,
                                color = Grove.InkSoft,
                                fontSize = 11.sp,
                            )
                            Text(
                                text = "${mems.size} ${if (mems.size == 1) "moment" else "moments"}",
                                fontFamily = NunitoSans,
                                color = Grove.InkSoft,
                                fontSize = 11.sp,
                            )
                            // A gentle hint that the tree is tappable.
                            Box(
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Grove.Accent.copy(alpha = 0.12f))
                                    .padding(horizontal = 9.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "tap to relive ↗",
                                    fontFamily = NunitoSans,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Grove.Accent,
                                    fontSize = 9.5.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Centered title (Grove style).
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "Your forest", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 26.sp, color = Grove.Ink)
            Text(
                text = "${weeks.size} ${if (weeks.size == 1) "month" else "months"}, planted together",
                fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft,
            )
            Text(
                text = "swipe to wander → · tap a tree to relive its month",
                fontFamily = NunitoSans, fontSize = 11.sp, color = Grove.InkFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }

    galleryWeek?.let { week ->
        WeekGalleryDialog(
            week = week,
            memories = memoriesFor(week),
            vm = vm,
            onPlayMontage = { montageWeek = week; galleryWeek = null },
            onClose = { galleryWeek = null },
        )
    }

    montageWeek?.let { week ->
        // Full-screen dialog so the montage covers everything — including the
        // bottom tab bar. Only the ✕ (or back) closes it.
        Dialog(
            onDismissRequest = { montageWeek = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Montage(
                week = week,
                memories = memoriesFor(week),
                vm = vm,
                onClose = { montageWeek = null },
            )
        }
    }
}

/** One bird drifting across the sky band, wrapping around the screen edges. */
private class SkyBird(
    val startX: Float,   // 0..1 of width
    val y: Float,        // 0..1 of height, kept in the upper band
    val speed: Float,    // fraction of width / second (sign = direction)
    val scale: Float,
    val bobAmp: Float,
    val bobFreq: Float,
    val phase: Float,
)

/**
 * 3–4 birds gliding across the top of the forest for a touch of life. They wander
 * only in the upper sky band (well above the trees), drift at varied speeds and
 * heights, and bob gently — never touching any tree.
 */
@Composable
private fun ForestBirds(scrollPx: Float) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.bird))
    val birds = remember {
        List(Random.nextInt(3, 5)) {
            SkyBird(
                startX = Random.nextFloat(),
                y = 0.12f + Random.nextFloat() * 0.16f,           // upper band 0.12..0.28
                speed = (0.05f + Random.nextFloat() * 0.06f) * (if (Random.nextBoolean()) 1f else -1f),
                scale = 0.7f + Random.nextFloat() * 0.45f,
                bobAmp = 0.008f + Random.nextFloat() * 0.018f,
                bobFreq = 0.5f + Random.nextFloat() * 0.8f,
                phase = Random.nextFloat() * 6.2832f,
            )
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                if (last != 0L) t += (n - last) / 1_000_000_000f
                last = n
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        birds.forEach { b ->
            // Own drift + a sky-parallax shift from the scroll (slower than the
            // trees, like distant clouds), all wrapped so birds stay on screen.
            var fx = (b.startX + b.speed * t - (scrollPx / wPx) * 0.12f) % 1.2f
            if (fx < 0f) fx += 1.2f
            fx -= 0.1f
            val yy = b.y + sin(t * b.bobFreq + b.phase) * b.bobAmp
            val faceLeft = b.speed < 0f
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        translationX = fx * wPx - size.width / 2f
                        translationY = yy * hPx - size.height / 2f
                        scaleX = b.scale * if (faceLeft) 1f else -1f
                        scaleY = b.scale
                    },
            )
        }
    }
}

/**
 * Standalone forest route (kept for compatibility). The app now reaches the
 * forest through the bottom tab in [MainShell]; this simply wraps [ForestPane]
 * with a back affordance.
 */
@Composable
fun ForestScreen(vm: MemoriesViewModel, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().groveBackground()) {
        ForestPane(vm = vm, contentPadding = PaddingValues(bottom = 24.dp))
        TextButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(start = 8.dp)) {
            Text("← Back", fontFamily = NunitoSans, color = Grove.InkSoft)
        }
    }
}
