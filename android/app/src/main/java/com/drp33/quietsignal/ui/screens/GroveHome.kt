package com.drp33.quietsignal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.Newsreader
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.util.AudioRecorder
import com.drp33.quietsignal.util.vibrateDoubleTap
import com.drp33.quietsignal.util.vibrateTick
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import com.drp33.quietsignal.viewmodels.ThreadsViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ============================================================= *
 *  SHELL  —  "This week" / "Threads" / "Forest" tabs + nav bar  *
 * ============================================================= */

enum class GroveTab { Week, Threads, Forest, Gallery }

/** Warm Grove background gradient shared by all tabs. */
fun Modifier.groveBackground(): Modifier = this.background(
    Brush.verticalGradient(
        0f to Grove.SkyTop,
        0.46f to Grove.Bg,
        1f to Grove.Bg,
    ),
)

/**
 * Hosts the role's home ("This week"), the shared Threads list, and the Forest
 * under one floating bottom-tab bar, swapping content in place (Grove style).
 * When a thread is opened (from here OR from the Gallery), [ThreadChatScreen]
 * is laid over everything. [week] is given bottom padding so its content clears
 * the nav bar.
 */
@Composable
fun MainShell(
    forestVm: MemoriesViewModel,
    threadsVm: ThreadsViewModel,
    emergencyActive: Boolean = false,
    onEmergencyAck: () -> Unit = {},
    week: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(GroveTab.Week) }
    // Clears the floating tab bar (which itself sits above the system nav bar).
    val pad = PaddingValues(bottom = 150.dp)
    val unread = threadsVm.unreadTotal

    Box(modifier = Modifier.fillMaxSize().groveBackground()) {
        when (tab) {
            GroveTab.Week -> week(pad)
            GroveTab.Threads -> ThreadsPane(vm = threadsVm, contentPadding = pad)
            GroveTab.Forest -> ForestPane(vm = forestVm, contentPadding = pad)
            GroveTab.Gallery -> MemoriesScreen(
                vm = forestVm,
                currentUserId = threadsVm.selfId,
                contentPadding = pad,
                onStartThread = { item, caption ->
                    threadsVm.openThread(item.objectName, item.type, item.sender, title = caption)
                },
            )
        }
        GroveBottomNav(
            tab = tab,
            unread = unread,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // The conversation view sits above the tabs, gallery and nav bar.
        if (threadsVm.activeAnchor != null) {
            ThreadChatScreen(vm = threadsVm, onClose = { threadsVm.closeThread() })
        }

        // The "wants to talk" nudge shows over every tab, not just "This week".
        if (emergencyActive) {
            WantsToTalkAlert(onAcknowledge = onEmergencyAck)
        }
    }
}

/**
 * A gentle wellbeing prompt shown to Sadie when Norman taps "Want to talk". It's
 * not an alarm — dismissed only by acknowledging, so it isn't missed. Lives at
 * the shell level so it appears across This week / Threads / Forest / Gallery.
 */
@Composable
private fun WantsToTalkAlert(onAcknowledge: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* require an explicit acknowledgement */ },
        containerColor = Grove.Surface,
        icon = { Text(text = "💛", fontSize = 40.sp) },
        title = {
            Text(
                text = "Norman would love to talk",
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                color = Grove.Ink,
                fontSize = 21.sp,
            )
        },
        text = {
            Text(
                text = "He's feeling a little lonely and would love to hear from you. Give him a call or send a moment when you can.",
                fontFamily = NunitoSans,
                color = Grove.InkSoft,
            )
        },
        confirmButton = {
            Button(
                onClick = onAcknowledge,
                colors = ButtonDefaults.buttonColors(containerColor = Grove.Accent),
            ) {
                Text(text = "I'll reach out", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
fun GroveBottomNav(tab: GroveTab, unread: Int, onSelect: (GroveTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(bottom = 18.dp)
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp))
            .background(Grove.Surface)
            .border(0.5.dp, Grove.Line, RoundedCornerShape(22.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem("This week", "🌳", tab == GroveTab.Week, Modifier.weight(1f)) { onSelect(GroveTab.Week) }
        NavItem("Threads", "💬", tab == GroveTab.Threads, Modifier.weight(1f), badge = unread) { onSelect(GroveTab.Threads) }
        NavItem("Forest", "🌲", tab == GroveTab.Forest, Modifier.weight(1f)) { onSelect(GroveTab.Forest) }
        NavItem("Gallery", "🖼", tab == GroveTab.Gallery, Modifier.weight(1f)) { onSelect(GroveTab.Gallery) }
    }
}

@Composable
private fun NavItem(label: String, glyph: String, active: Boolean, modifier: Modifier, badge: Int = 0, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Grove.Accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Text(text = glyph, fontSize = 19.sp)
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-7).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0524B)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badge > 9) "9+" else badge.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        style = LocalTextStyle.current.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = NunitoSans,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Grove.Accent else Grove.InkFaint,
        )
    }
}

/* ============================================================= *
 *  HEADER + SAFETY STRIP                                          *
 * ============================================================= */

@Composable
fun GroveHeader(title: String, subtitle: String, momentCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 31.sp, color = Grove.Ink)
            Spacer(Modifier.height(3.dp))
            Text(text = subtitle, fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft)
        }
        Spacer(Modifier.size(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "THIS WEEK", fontFamily = NunitoSans, fontSize = 11.sp, letterSpacing = 1.2.sp, color = Grove.InkFaint)
            Text(
                text = "$momentCount ${if (momentCount == 1) "moment" else "moments"}",
                fontFamily = NunitoSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Grove.Ink,
            )
        }
    }
}

fun safetyText(
    mood: TreeMood,
    peerName: String? = null,
    peerLastSeenToday: Boolean = false,
    lastMomentEpoch: Long? = null
): String {
    if (mood == TreeMood.THRIVING && peerName != null) {
        val presence = if (peerLastSeenToday) "$peerName's been here today" else "You're all caught up"
        val moment = lastMomentEpoch?.let {
            val time = SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(it * 1000))
            " · last moment $time"
        } ?: ""
        return "$presence$moment"
    }

    return when (mood) {
        TreeMood.THRIVING -> "You're all caught up — the grove is thriving 🌿"
        TreeMood.OKAY -> "A gentle week so far. A moment keeps it bright."
        TreeMood.FADING -> "It's been quiet. Share a moment to add colour."
    }
}

@Composable
fun SafetyStrip(
    mood: TreeMood,
    modifier: Modifier = Modifier,
    peerName: String? = null,
    peerLastSeenToday: Boolean = false,
    lastMomentEpoch: Long? = null
) {
    val dot = if (mood == TreeMood.THRIVING) Grove.Foliage else Grove.Accent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(14.dp), clip = false)
            .clip(RoundedCornerShape(14.dp))
            .background(Grove.Surface)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        Text(
            text = safetyText(mood, peerName, peerLastSeenToday, lastMomentEpoch),
            fontFamily = NunitoSans,
            fontSize = 13.sp,
            color = Grove.InkSoft
        )
    }
}

/* ============================================================= *
 *  INPUT ROW  —  Voice · Photo · Hello · Note                    *
 * ============================================================= */

@Composable
fun GroveInputRow(
    onVoiceRecorded: (ByteArray) -> Unit,
    onPhotoCaptured: (ByteArray) -> Unit,
    onWater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        GroveVoiceTile(onVoiceRecorded)
        GrovePhotoTile(onPhotoCaptured)
        GroveHelloTile(onWater)
    }
}

@Composable
private fun GroveTile(label: String, glyph: String, tint: Color, active: Boolean = false, pulse: Float = 0f, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // A soft halo that breathes with the mic's loudness while recording.
            if (pulse > 0f) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .graphicsLayer {
                            val s = 1f + pulse * 0.7f
                            scaleX = s; scaleY = s
                            alpha = 0.18f + pulse * 0.30f
                        }
                        .clip(RoundedCornerShape(22.dp))
                        .background(tint),
                )
            }
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp), clip = false)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Grove.Surface)
                    .border(1.5.dp, tint.copy(alpha = if (active) 0.95f else 0.32f), RoundedCornerShape(18.dp))
                    .clickable { onClick() },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = glyph, fontSize = 24.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(text = label, fontFamily = NunitoSans, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) tint else Grove.InkSoft)
    }
}

@Composable
private fun GroveVoiceTile(onRecorded: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }

    val requestPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            recorder.start(); isRecording = true; context.vibrateTick()
        }
    }

    // Poll the mic's loudness while recording to drive the reactive halo.
    var amp by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isRecording) {
        if (!isRecording) { amp = 0f; return@LaunchedEffect }
        while (isRecording) {
            amp = (recorder.amplitude() / 14000f).coerceIn(0f, 1f)
            delay(70)
        }
        amp = 0f
    }
    val pulse by animateFloatAsState(targetValue = if (isRecording) amp else 0f, label = "micPulse")

    GroveTile(
        label = if (isRecording) "Stop" else "Voice",
        glyph = if (isRecording) "⏺" else "🎙️",
        tint = Grove.Voice,
        active = isRecording,
        pulse = pulse,
        onClick = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            when {
                isRecording -> {
                    val bytes = recorder.stop()
                    isRecording = false
                    context.vibrateDoubleTap()
                    if (bytes != null) onRecorded(bytes)
                }
                granted -> {
                    recorder.start(); isRecording = true; context.vibrateTick()
                }
                else -> requestPerm.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
    )
}

@Composable
private fun GrovePhotoTile(onCaptured: (ByteArray) -> Unit) {
    val context = LocalContext.current
    var showCamera by remember { mutableStateOf(false) }

    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCamera = true
    }

    GroveTile(label = "Photo", glyph = "📷", tint = Grove.Photo, onClick = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) showCamera = true else requestCamera.launch(Manifest.permission.CAMERA)
    })

    if (showCamera) {
        CameraCaptureDialog(
            onCaptured = { onCaptured(it); showCamera = false },
            onClose = { showCamera = false },
        )
    }
}

@Composable
private fun GroveHelloTile(onWater: () -> Unit) {
    val context = LocalContext.current
    GroveTile(label = "Hello", glyph = "👋", tint = Grove.Water, onClick = {
        context.vibrateTick(); onWater()
    })
}


