package com.drp33.quietsignal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.model.ThreadMessage
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.Newsreader
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.util.AudioPlayer
import com.drp33.quietsignal.util.AudioRecorder
import com.drp33.quietsignal.util.decodeSampledBitmap
import com.drp33.quietsignal.util.vibrateDoubleTap
import com.drp33.quietsignal.util.vibrateTick
import com.drp33.quietsignal.viewmodels.ThreadsViewModel

/**
 * The conversation view for one thread. WhatsApp-style bubbles over the warm
 * Grove backdrop: the anchored memory is pinned at the top, then every reply.
 * Compose with text, a voice note, or a snap from the bottom composer.
 */
@Composable
fun ThreadChatScreen(vm: ThreadsViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val anchor = vm.activeAnchor ?: return
    val selfId = vm.selfId

    // Decode the anchored memory's image (photo memories only) for the header + pin.
    var anchorImage by remember(anchor) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(anchor) {
        if (vm.activeType == "photo") {
            vm.loadMediaBytes(anchor) { bytes ->
                anchorImage = decodeSampledBitmap(bytes, 600, 600)?.asImageBitmap()
            }
        }
    }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size) // +1 header item
    }

    Box(modifier = Modifier.fillMaxSize().groveBackground()) {
        // imePadding is NOT used here because adjustResize in Manifest handles
        // the resizing of the window. Adding it here would cause double-padding.
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- header ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Grove.Surface)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                GroveCircleButton(glyph = "‹", contentDescription = "Back", onClick = onClose)
                AnchorThumb(type = vm.activeType, image = anchorImage, prompt = vm.activeIsPrompt, size = 42)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (vm.activeIsPrompt) "A memory worth revisiting"
                        else vm.threadTitle(anchor, vm.activeSender, vm.activeType),
                        fontFamily = NunitoSans, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = Grove.Ink, maxLines = 1,
                    )
                    Text(
                        text = if (vm.activeIsPrompt) "Grove resurfaced this for you both" else "Conversation",
                        fontFamily = NunitoSans, fontSize = 12.sp, color = Grove.InkFaint, maxLines = 1,
                    )
                }
            }

            // ---- messages ----
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Column {
                        if (vm.activeIsPrompt) {
                            PromptBanner()
                            Spacer(Modifier.height(8.dp))
                        }
                        AnchorPin(type = vm.activeType, title = vm.threadTitle(anchor, vm.activeSender, vm.activeType), image = anchorImage)
                        Spacer(Modifier.height(6.dp))
                    }
                }
                items(vm.messages, key = { it.id }) { msg ->
                    MessageBubble(msg = msg, isSelf = msg.senderId == selfId, vm = vm)
                }
            }

            // ---- composer ----
            ChatComposer(
                onSendText = { vm.sendText(it) },
                onSendVoice = { vm.sendVoice(it) },
                onSendPhoto = { vm.sendPhoto(it) },
            )
        }
    }
}

@Composable
private fun PromptBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Grove.Accent.copy(alpha = 0.12f))
            .border(1.dp, Grove.Accent.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✨", fontSize = 14.sp)
        Text(
            text = "It's been quiet lately — Grove picked out a moment you might want to talk about.",
            fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft, lineHeight = 18.sp,
        )
    }
}

@Composable
private fun AnchorThumb(type: String, image: ImageBitmap?, prompt: Boolean, size: Int) {
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    prompt -> Grove.Accent
                    type == "photo" -> Grove.Photo.copy(alpha = 0.22f)
                    else -> Grove.Voice.copy(alpha = 0.20f)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (prompt) Text("✨", fontSize = 18.sp)
        else if (type == "photo" && image != null) Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(if (type == "photo") "📸" else "🎙", fontSize = 18.sp)
    }
}

/** The memory the conversation is about, pinned at the top of the chat. */
@Composable
private fun AnchorPin(type: String, title: String, image: ImageBitmap?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Grove.Surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                .background(if (type == "photo") Grove.Photo.copy(alpha = 0.18f) else Grove.Voice.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (type == "photo" && image != null) Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(if (type == "photo") "📸" else "🎙", fontSize = 22.sp)
        }
        Column(Modifier.weight(1f)) {
            Text("This conversation is about", fontFamily = NunitoSans, fontSize = 11.sp, color = Grove.InkFaint, fontWeight = FontWeight.SemiBold)
            Text(title, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Grove.Ink)
        }
    }
}

@Composable
private fun MessageBubble(msg: ThreadMessage, isSelf: Boolean, vm: ThreadsViewModel) {
    val bg = if (isSelf) Grove.Accent else Grove.Surface
    val fg = if (isSelf) Color.White else Grove.Ink
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (isSelf) 18.dp else 5.dp, bottomEnd = if (isSelf) 5.dp else 18.dp,
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
    ) {
        if (!isSelf) {
            Text(msg.sender, fontFamily = NunitoSans, fontSize = 11.5.sp, color = Grove.InkFaint, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, bottom = 3.dp))
        }
        Box(
            modifier = Modifier.widthIn(max = 270.dp).clip(shape).background(bg)
                .padding(if (msg.kind == "photo") 4.dp else 0.dp),
        ) {
            when (msg.kind) {
                "text" -> Text(msg.text, fontFamily = NunitoSans, fontSize = 15.sp, color = fg, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp))
                "photo" -> Box(modifier = Modifier.width(196.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp)).background(Grove.Photo.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    val img = msg.image
                    if (img != null) Image(bitmap = img, contentDescription = "Snap", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text("📷", fontSize = 30.sp)
                }
                "voice" -> VoiceBubble(msg = msg, isSelf = isSelf, onToggle = { onBytes -> msg.mediaObject?.let { vm.loadMediaBytes(it, onBytes) } })
            }
        }
        Text(threadTime(msg.epoch), fontFamily = NunitoSans, fontSize = 10.5.sp, color = Grove.InkFaint, modifier = Modifier.padding(top = 3.dp, start = 6.dp, end = 6.dp))
    }
}

@Composable
private fun VoiceBubble(msg: ThreadMessage, isSelf: Boolean, onToggle: ((ByteArray) -> Unit) -> Unit) {
    val context = LocalContext.current
    val player = remember { AudioPlayer(context) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).widthIn(min = 168.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(if (isSelf) Color.White.copy(alpha = 0.22f) else Grove.Voice)
                .clickable {
                    if (playing) { player.pause(); playing = false }
                    else onToggle { bytes -> player.play(bytes) { playing = false }; playing = true }
                },
            contentAlignment = Alignment.Center,
        ) { Text(if (playing) "⏸" else "▶", color = Color.White, fontSize = 15.sp) }
        ChatWaveform(color = if (isSelf) Color.White.copy(alpha = 0.9f) else Grove.Voice, bars = 18, maxHeight = 22)
    }
}

@Composable
private fun ChatComposer(onSendText: (String) -> Unit, onSendVoice: (ByteArray) -> Unit, onSendPhoto: (ByteArray) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    val recorder = remember { AudioRecorder(context) }

    val requestMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { recorder.start(); recording = true; context.vibrateTick() }
    }
    val requestCam = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCamera = true
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Grove.Surface)
            .navigationBarsPadding() // Only clear the system nav bar
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (recording) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE0524B)))
            ChatWaveform(color = Grove.Voice, modifier = Modifier.weight(1f), bars = 26, maxHeight = 26)
            CircleSend(glyph = "■") {
                val bytes = recorder.stop(); recording = false; context.vibrateDoubleTap()
                if (bytes != null) onSendVoice(bytes)
            }
        } else {
            ComposerIcon(glyph = "📷") {
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) showCamera = true else requestCam.launch(Manifest.permission.CAMERA)
            }
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Message…", fontFamily = NunitoSans, color = Grove.InkFaint) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = NunitoSans, fontSize = 15.sp, color = Grove.Ink),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Grove.Surface2,
                    unfocusedContainerColor = Grove.Surface2,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = Grove.Accent,
                ),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { onSendText(text); text = "" } }),
            )
            if (text.isNotBlank()) {
                CircleSend(glyph = "➤") { onSendText(text); text = "" }
            } else {
                ComposerIcon(glyph = "🎙") {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (granted) { recorder.start(); recording = true; context.vibrateTick() } else requestMic.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    if (showCamera) {
        CameraCaptureDialog(
            onCaptured = { onSendPhoto(it); showCamera = false },
            onClose = { showCamera = false },
        )
    }
}

@Composable
private fun ComposerIcon(glyph: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(glyph, fontSize = 20.sp)
    }
}

@Composable
private fun CircleSend(glyph: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Grove.Accent).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(glyph, color = Color.White, fontSize = 18.sp)
    }
}
