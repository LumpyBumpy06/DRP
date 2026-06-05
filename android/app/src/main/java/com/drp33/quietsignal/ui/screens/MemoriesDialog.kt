package com.drp33.quietsignal.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun MemoriesDialog(vm: MemoriesViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember { AudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var playing by remember { mutableStateOf<String?>(null) }
    var fullPhoto by remember { mutableStateOf<ImageBitmap?>(null) }

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

                    Spacer(modifier = Modifier.height(14.dp))

                    val memories = vm.memories
                    when {
                        vm.loading && memories.isEmpty() ->
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF4CAF50))
                            }

                        memories.isEmpty() ->
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "🌱 No memories yet.\nShare a snap or a voice memo to start your story.",
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
                            items(memories, key = { it.objectName }) { item ->
                                MemoryTile(
                                    item = item,
                                    isPlaying = playing == item.objectName,
                                    onClick = {
                                        if (item.type == "photo") {
                                            item.image?.let { fullPhoto = it }
                                        } else if (playing == item.objectName) {
                                            player.pause()
                                            playing = null
                                        } else {
                                            vm.loadMediaBytes(item.objectName) { bytes ->
                                                player.play(bytes) { playing = null }
                                                playing = item.objectName
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fullPhoto?.let { img ->
        Dialog(
            onDismissRequest = { fullPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { fullPhoto = null }
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = img,
                    contentDescription = "Memory",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
        }
    }
}

@Composable
private fun MemoryTile(item: MemoryItem, isPlaying: Boolean, onClick: () -> Unit) {
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
                    Text(text = if (isPlaying) "⏸" else "🎤", fontSize = 40.sp)
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
