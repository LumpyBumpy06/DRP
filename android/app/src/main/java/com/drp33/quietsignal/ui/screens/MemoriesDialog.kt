package com.drp33.quietsignal.ui.screens

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drp33.quietsignal.model.MemoryItem
import com.drp33.quietsignal.util.AudioPlayer
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import kotlinx.coroutines.delay
import java.io.OutputStream

// Warm, sunny, nature-y palette to match the tree and feel positive.
private val BOARD_TOP = Color(0xFFFFFDF6) // warm white
private val BOARD_BOTTOM = Color(0xFFE6F4E7) // soft mint
private val TITLE_GREEN = Color(0xFF2E7D32)
private val ACCENT_GREEN = Color(0xFF66807A)
private val SUBTITLE_GREEN = Color(0xFF7CA56B)
private val VOICE_TILE = listOf(Color(0xFFA5D6A7), Color(0xFF66BB6A))
private val CAPTION_SCRIM = listOf(Color.Transparent, Color(0xCC1B5E20))

/**
 * A pop-up "board" of every voice memo and snap ever shared. Tap a snap to view
 * it full, tap a voice memo to play it. ✕ to close.
 */
@Composable
fun MemoriesDialog(vm: MemoriesViewModel, currentUserId: Int, onClose: () -> Unit) {
    var expandedItem by remember { mutableStateOf<MemoryItem?>(null) }
    // Gallery filter — defaults to photos; tap "Voice" to see the voice messages.
    var filter by remember { mutableStateOf("photo") }

    LaunchedEffect(Unit) { vm.load() }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BOARD_TOP),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BOARD_TOP, BOARD_BOTTOM)))) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📖 Our Memories",
                                color = TITLE_GREEN,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "every moment you've grown together 🌱",
                                color = SUBTITLE_GREEN,
                                fontSize = 13.sp,
                            )
                        }
                        TextButton(onClick = onClose) {
                            Text(text = "✕", color = ACCENT_GREEN, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Filter toggle: Photos (default) / Voice.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterPill(
                            label = "📸 Photos",
                            selected = filter == "photo",
                            onClick = { filter = "photo" },
                        )
                        FilterPill(
                            label = "🎤 Voice",
                            selected = filter == "voice",
                            onClick = { filter = "voice" },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val memories = vm.memories
                    val filtered = remember(memories, filter) { memories.filter { it.type == filter } }
                    when {
                        vm.loading && memories.isEmpty() ->
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF4CAF50))
                            }

                        filtered.isEmpty() ->
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (filter == "photo") {
                                        "🌱 No photos yet.\nShare a snap to start your story."
                                    } else {
                                        "🌱 No voice messages yet.\nRecord a voice memo to start your story."
                                    },
                                    color = SUBTITLE_GREEN,
                                    textAlign = TextAlign.Center,
                                )
                            }

                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(filtered, key = { it.objectName }) { item ->
                                MemoryTile(
                                    item = item,
                                    onClick = { expandedItem = item },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    expandedItem?.let { item ->
        ExpandedMemoryDialog(
            item = item,
            vm = vm,
            currentUserId = currentUserId,
            onClose = { expandedItem = null }
        )
    }
}

/** A small pill button used for the Photos / Voice gallery filter. */
@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) TITLE_GREEN else Color.White
    val content = if (selected) Color.White else ACCENT_GREEN
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = container,
        border = if (selected) null else BorderStroke(1.dp, SUBTITLE_GREEN.copy(alpha = 0.5f)),
    ) {
        Text(
            text = label,
            color = content,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ExpandedMemoryDialog(
    item: MemoryItem,
    vm: MemoriesViewModel,
    currentUserId: Int,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val player = remember { AudioPlayer(context) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.position()
            delay(50)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BOARD_TOP),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (item.type == "photo") {
                    item.image?.let { img ->
                        Image(
                            bitmap = img,
                            contentDescription = "Memory",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp)),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.verticalGradient(VOICE_TILE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        player.pause()
                                        isPlaying = false
                                    } else {
                                        vm.loadMediaBytes(item.objectName) { bytes ->
                                            durationMs = player.play(bytes) {
                                                isPlaying = false
                                                positionMs = 0
                                            }
                                            isPlaying = true
                                        }
                                    }
                                },
                                modifier = Modifier.size(80.dp)
                            ) {
                                Text(text = if (isPlaying) "⏸" else "▶", fontSize = 50.sp)
                            }
                            if (durationMs > 0) {
                                Text(
                                    text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Reshare button
                    Button(
                        onClick = {
                            vm.reshare(item, currentUserId) {
                                Toast.makeText(context, "Memory reshared!", Toast.LENGTH_SHORT).show()
                                onClose()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TITLE_GREEN)
                    ) {
                        Text("🔄 Reshare")
                    }

                    // Download button
                    Button(
                        onClick = {
                            vm.loadMediaBytes(item.objectName) { bytes ->
                                saveToDisk(context, bytes, item.type, item.objectName)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ACCENT_GREEN)
                    ) {
                        Text("📥 Download")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onClose) {
                    Text("Close", color = ACCENT_GREEN, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun saveToDisk(context: android.content.Context, bytes: ByteArray, type: String, objectName: String) {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, objectName.substringAfterLast("/"))
        put(MediaStore.MediaColumns.MIME_TYPE, if (type == "photo") "image/jpeg" else "audio/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val folder = if (type == "photo") Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MUSIC
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/QuietSignal")
        }
    }

    val uri = if (type == "photo") {
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    } else {
        resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
    }

    uri?.let {
        resolver.openOutputStream(it)?.use { outputStream ->
            outputStream.write(bytes)
            Toast.makeText(context, "Saved to ${if (type == "photo") "Gallery" else "Music"}", Toast.LENGTH_SHORT).show()
        }
    } ?: run {
        Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun MemoryTile(item: MemoryItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.type == "photo") {
                val image = item.image
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = "Snap from ${item.sender}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFFC8E6C9)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "📷", fontSize = 30.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(VOICE_TILE)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "🎤", fontSize = 40.sp)
                }
            }

            // Soft caption scrim along the bottom.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(CAPTION_SCRIM))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "${if (item.type == "photo") "📸" else "🎤"} ${item.sender} · ${relativeTime(item.epoch)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun relativeTime(epochSec: Long): String {
    val diff = System.currentTimeMillis() / 1000 - epochSec
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        else -> "${diff / 86400}d ago"
    }
}
