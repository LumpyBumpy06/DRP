package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Label from a week-start epoch (seconds). Includes the time so the short test
 * "weeks" (60s apart) stay visibly distinct. */
private fun weekLabel(weekStart: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(weekStart * 1000))

/** A row of soft rolling hills across [x0]..[x1], baseline [baseY], crests
 * rising [amp] above it every [wavelength]px. Closed down to [bottom]. */
private fun rollingHill(x0: Float, x1: Float, baseY: Float, amp: Float, wavelength: Float, bottom: Float): Path =
    Path().apply {
        moveTo(x0, baseY)
        var x = x0
        while (x < x1) {
            val nx = x + wavelength
            quadraticBezierTo((x + nx) / 2f, baseY - amp, nx, baseY)
            x = nx
        }
        lineTo(x1, bottom)
        lineTo(x0, bottom)
        close()
    }

/**
 * The shared forest as a Grove tab with a 2.5D parallax feel: drifting clouds,
 * two layers of hills that slide as you swipe, and trees that alternate
 * near (large, low, in front) / far (small, high, faded) for depth. Tap a tree
 * to relive that week as a [Montage].
 */
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

    val memoriesByWeek = remember(vm.memories) {
        vm.memories.groupBy { (it.epoch / WEEK_SECONDS) * WEEK_SECONDS }
    }
    var montageWeek by remember { mutableStateOf<ForestWeek?>(null) }

    val currentWeekStart = (System.currentTimeMillis() / 1000 / WEEK_SECONDS) * WEEK_SECONDS
    val weeks = vm.forestWeeks.filter { it.weekStart < currentWeekStart }

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

            translate(left = -scrollPx * 0.08f) {
                val cy = h * 0.14f
                val xs = floatArrayOf(0.16f, 0.52f, 0.86f, 1.22f, 1.6f)
                xs.forEachIndexed { i, fx ->
                    val cx = w * fx
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

            translate(left = -scrollPx * 0.18f) {
                drawPath(
                    rollingHill(-w, w * 2.4f, h * 0.62f, h * 0.085f, w * 0.62f, h),
                    Grove.FoliageRest.copy(alpha = 0.32f),
                )
            }
            translate(left = -scrollPx * 0.32f) {
                drawPath(
                    rollingHill(-w, w * 2.8f, h * 0.74f, h * 0.07f, w * 0.5f, h),
                    Grove.Foliage2.copy(alpha = 0.22f),
                )
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

        if (weeks.isEmpty()) {
            Text(
                text = "🌱 Your forest is just beginning.\nShare moments to grow this week's tree.",
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 112.dp, top = 116.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                itemsIndexed(display, key = { _, w -> w.weekStart }) { index, week ->
                    val mems = memoriesByWeek[week.weekStart].orEmpty()
                    val far = index % 2 == 1
                    Column(
                        modifier = Modifier
                            .width(156.dp)
                            .graphicsLayer {
                                // Lift the entire column (tree + labels) for far trees
                                translationY = if (far) (-72).dp.toPx() else 0f
                            }
                            .clickable { montageWeek = week },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    // Scale ONLY the tree graphic
                                    val s = if (far) 0.66f else 0.94f
                                    scaleX = s
                                    scaleY = s
                                    alpha = if (far) 0.88f else 1f
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            WateringTree(stage = week.stage, deathLevel = week.deathLevel)
                        }

                        Text(
                            text = weekLabel(week.weekStart),
                            fontFamily = Newsreader,
                            fontWeight = FontWeight.Medium,
                            color = Grove.Ink,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = "${mems.size} ${if (mems.size == 1) "moment" else "moments"}",
                            fontFamily = NunitoSans,
                            color = Grove.InkSoft,
                            fontSize = 11.sp,
                        )
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
                text = "${weeks.size} ${if (weeks.size == 1) "week" else "weeks"}, planted together",
                fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft,
            )
            Text(
                text = "swipe to wander →",
                fontFamily = NunitoSans, fontSize = 11.sp, color = Grove.InkFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }

    montageWeek?.let { week ->
        Montage(
            week = week,
            memories = memoriesByWeek[week.weekStart].orEmpty(),
            vm = vm,
            onClose = { montageWeek = null },
        )
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
