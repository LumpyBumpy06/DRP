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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

/**
 * The shared forest as a Grove tab: soft sky, layered hills and a ground band,
 * with one frozen [WateringTree] per completed week sitting on the soil. Tap a
 * tree to relive that week as a [Montage]. No back button — it's a bottom tab.
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Layered hills (drawn over the shell's sky gradient).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val far = Path().apply {
                moveTo(0f, h * 0.64f)
                quadraticBezierTo(w * 0.28f, h * 0.56f, w * 0.58f, h * 0.62f)
                quadraticBezierTo(w * 0.84f, h * 0.67f, w, h * 0.60f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(far, Grove.FoliageRest.copy(alpha = 0.32f))
            val mid = Path().apply {
                moveTo(0f, h * 0.74f)
                quadraticBezierTo(w * 0.34f, h * 0.66f, w * 0.7f, h * 0.72f)
                quadraticBezierTo(w * 0.9f, h * 0.75f, w, h * 0.69f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(mid, Grove.Foliage2.copy(alpha = 0.22f))
        }

        // Ground band at the bottom (above the nav bar).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 116.dp, top = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                items(display, key = { it.weekStart }) { week ->
                    val mems = memoriesByWeek[week.weekStart].orEmpty()
                    Column(
                        modifier = Modifier
                            .width(210.dp)
                            .clickable { montageWeek = week },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        WateringTree(stage = week.stage, deathLevel = week.deathLevel)
                        Text(
                            text = weekLabel(week.weekStart),
                            fontFamily = Newsreader,
                            fontWeight = FontWeight.Medium,
                            color = Grove.Ink,
                            fontSize = 17.sp,
                        )
                        Text(
                            text = "${mems.size} ${if (mems.size == 1) "moment" else "moments"}",
                            fontFamily = NunitoSans,
                            color = Grove.InkSoft,
                            fontSize = 12.sp,
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
