package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.model.ForestWeek
import com.drp33.quietsignal.model.MemoryItem
import com.drp33.quietsignal.model.WEEK_SECONDS
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The start-of-week (aligned to WEEK_SECONDS) that a memory belongs to. */
private fun MemoryItem.weekStart(): Long = (epoch / WEEK_SECONDS) * WEEK_SECONDS

/** Label from a week-start epoch (seconds). Includes the time so the short
 * test "weeks" (60s apart, same day) are visibly distinct. For real 7-day weeks
 * switch the pattern back to "'Week of' d MMM". */
private fun weekLabel(weekStart: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(weekStart * 1000))

/**
 * The shared forest: one frozen [WateringTree] per past week (newest first), each
 * in the real state it ended that week in. Tap a tree to relive that week's
 * memories as a [Montage].
 */
@Composable
fun ForestScreen(vm: MemoriesViewModel, onBack: () -> Unit) {
    // Reload now, then again right after each week rollover (no constant polling —
    // it sleeps until the next boundary). A week only joins the forest once it has
    // ended, so nothing is added mid-week.
    LaunchedEffect(Unit) {
        while (true) {
            vm.load()
            vm.loadForest()
            val periodMs = WEEK_SECONDS * 1000
            val nowMs = System.currentTimeMillis()
            delay(periodMs - (nowMs % periodMs) + 300)
        }
    }

    val memoriesByWeek = remember(vm.memories) { vm.memories.groupBy { it.weekStart() } }
    var montageWeek by remember { mutableStateOf<ForestWeek?>(null) }

    // Only COMPLETED weeks appear — the in-progress current week is excluded, so a
    // tree is added to the forest (with all its memories at once) after the week
    // ends, never dynamically while you're still adding to it.
    val currentWeekStart = (System.currentTimeMillis() / 1000 / WEEK_SECONDS) * WEEK_SECONDS
    val weeks = vm.forestWeeks.filter { it.weekStart < currentWeekStart }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFEAF3EA), Color(0xFFF1ECE0)))),
    ) {
        if (weeks.isEmpty()) {
            Text(
                text = "🌱 Your forest is just beginning.\nShare moments to grow this week's tree.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(40.dp),
            )
        } else {
            // LazyRow so only on-screen trees compose/animate — a plain Row would
            // build (and run a frame loop for) every tree at once and freeze the UI.
            // Newest week first so the latest tree greets you on the left.
            val display = weeks.asReversed()
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                items(display, key = { it.weekStart }) { week ->
                    val mems = memoriesByWeek[week.weekStart].orEmpty()
                    Column(
                        modifier = Modifier
                            .width(210.dp)
                            .clickable { montageWeek = week }
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        WateringTree(stage = week.stage, deathLevel = week.deathLevel)
                        Text(
                            text = weekLabel(week.weekStart),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${mems.size} ${if (mems.size == 1) "moment" else "moments"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        // Header.
        Column(modifier = Modifier.statusBarsPadding().padding(start = 8.dp, top = 4.dp)) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text(
                text = "Your forest · ${weeks.size} ${if (weeks.size == 1) "week" else "weeks"}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp),
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
