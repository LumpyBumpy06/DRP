package com.drp33.quietsignal.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.NunitoSans
import kotlin.math.abs
import kotlin.math.sin

/* ============================================================= *
 *  Small shared Grove UI atoms used by the new Gallery / Threads
 *  / Settings / Prompt features. Theme-token only, no new colours.
 * ============================================================= */

/** Circular header action button (e.g. Gallery / Settings) with an emoji glyph. */
@Composable
fun GroveCircleButton(glyph: String, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(6.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Grove.Surface)
            .border(0.5.dp, Grove.Line, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, fontSize = 18.sp)
    }
}

/** Gallery + Settings pair, right-aligned, for the home header. */
@Composable
fun GroveHeaderActions(onGallery: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        GroveCircleButton(glyph = "🖼", contentDescription = "Gallery", onClick = onGallery)
        GroveCircleButton(glyph = "⚙️", contentDescription = "Settings", onClick = onSettings)
    }
}

/** An iOS-style switch in the Grove palette (foliage = on). */
@Composable
fun GroveSwitch(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val knobOffset by animateDpAsState(targetValue = if (on) 21.dp else 0.dp, label = "switch")
    Box(
        modifier = modifier
            .width(52.dp)
            .height(31.dp)
            .clip(CircleShape)
            .background(if (on) Grove.Foliage2 else Grove.Ink.copy(alpha = 0.18f))
            .clickable { onChange(!on) }
            .padding(2.5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobOffset)
                .size(26.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * The gentle "remember this?" card shown on the home screen when the tree is
 * quiet and prompts are enabled. Tapping opens the resurfaced memory's thread.
 */
@Composable
fun PromptCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Grove.Accent.copy(alpha = 0.10f))
            .border(1.dp, Grove.Accent.copy(alpha = 0.32f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Grove.Accent),
            contentAlignment = Alignment.Center,
        ) { Text(text = "✨", fontSize = 18.sp) }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontFamily = NunitoSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Grove.Ink)
            Spacer(Modifier.height(1.dp))
            Text(text = subtitle, fontFamily = NunitoSans, fontSize = 12.5.sp, color = Grove.InkSoft, maxLines = 1)
        }
        Text(text = "›", fontSize = 22.sp, color = Grove.Accent)
    }
}

/** A simple static voice-waveform strip (deterministic bar heights). */
@Composable
fun ChatWaveform(color: Color, modifier: Modifier = Modifier, bars: Int = 22, maxHeight: Int = 24) {
    Row(
        modifier = modifier.height(maxHeight.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(bars) { i ->
            val frac = 0.28f + abs(sin(i * 1.3 + 1.0)).toFloat() * 0.72f
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((maxHeight * frac).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/** "just now / 5m / 3h / Mon 14:02" style label for a thread/message epoch. */
fun threadTime(epochSec: Long): String {
    if (epochSec <= 0) return ""
    val diff = System.currentTimeMillis() / 1000 - epochSec
    return when {
        diff < 60 -> "now"
        diff < 3600 -> "${diff / 60}m"
        diff < 86400 -> "${diff / 3600}h"
        diff < 604800 -> "${diff / 86400}d"
        else -> {
            val fmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            fmt.format(java.util.Date(epochSec * 1000))
        }
    }
}
