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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
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

/* ============================================================= *
 *  SHELL  —  "This week" (home) / "Forest" tabs + floating nav  *
 * ============================================================= */

enum class GroveTab { Week, Forest }

/** Warm Grove background gradient shared by both tabs. */
fun Modifier.groveBackground(): Modifier = this.background(
    Brush.verticalGradient(
        0f to Grove.SkyTop,
        0.46f to Grove.Bg,
        1f to Grove.Bg,
    ),
)

/**
 * Hosts the role's home ("This week") and the shared Forest under one floating
 * bottom-tab bar, swapping content in place (Grove style) rather than pushing a
 * route. [week] is given bottom padding so its content clears the nav bar.
 */
@Composable
fun MainShell(
    forestVm: MemoriesViewModel,
    week: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(GroveTab.Week) }
    val pad = PaddingValues(bottom = 104.dp)

    Box(modifier = Modifier.fillMaxSize().groveBackground()) {
        when (tab) {
            GroveTab.Week -> week(pad)
            GroveTab.Forest -> ForestPane(vm = forestVm, contentPadding = pad)
        }
        GroveBottomNav(
            tab = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun GroveBottomNav(tab: GroveTab, onSelect: (GroveTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 14.dp)
            .padding(bottom = 26.dp)
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp))
            .background(Grove.Surface)
            .border(0.5.dp, Grove.Line, RoundedCornerShape(22.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem("This week", "🌳", tab == GroveTab.Week, Modifier.weight(1f)) { onSelect(GroveTab.Week) }
        NavItem("Forest", "🌲", tab == GroveTab.Forest, Modifier.weight(1f)) { onSelect(GroveTab.Forest) }
    }
}

@Composable
private fun NavItem(label: String, glyph: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Grove.Accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = glyph, fontSize = 19.sp)
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

fun safetyText(mood: TreeMood): String = when (mood) {
    TreeMood.THRIVING -> "You're all caught up — the grove is thriving 🌿"
    TreeMood.OKAY -> "A gentle week so far. A moment keeps it bright."
    TreeMood.FADING -> "It's been quiet lately — share a moment to bring colour back."
}

@Composable
fun SafetyStrip(mood: TreeMood, modifier: Modifier = Modifier) {
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
        Text(text = safetyText(mood), fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft)
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
    onNote: (String) -> Unit,
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
        GroveNoteTile(onNote)
    }
}

@Composable
private fun GroveTile(label: String, glyph: String, tint: Color, active: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

    GroveTile(
        label = if (isRecording) "Stop" else "Voice",
        glyph = if (isRecording) "⏺" else "🎙️",
        tint = Grove.Voice,
        active = isRecording,
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

@Composable
private fun GroveNoteTile(onSend: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    GroveTile(label = "Note", glyph = "📝", tint = Grove.Note, onClick = { open = true })
    if (open) {
        NoteComposerDialog(onCancel = { open = false }, onSend = { open = false; onSend(it) })
    }
}

@Composable
private fun NoteComposerDialog(onCancel: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Grove.Surface,
        title = { Text(text = "Leave a note", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 22.sp, color = Grove.Ink) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("A few words for the tree…", fontFamily = NunitoSans, color = Grove.InkFaint) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSend(text.trim()) }, enabled = text.isNotBlank()) {
                Text(text = "Plant it", fontFamily = NunitoSans, fontWeight = FontWeight.Bold, color = Grove.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(text = "Cancel", fontFamily = NunitoSans, color = Grove.InkSoft) }
        },
    )
}
