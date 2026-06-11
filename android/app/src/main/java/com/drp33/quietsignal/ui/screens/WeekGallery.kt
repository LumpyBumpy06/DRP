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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drp33.quietsignal.model.ForestWeek
import com.drp33.quietsignal.model.MemoryItem
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.Newsreader
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.util.AudioPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun galleryLabel(weekStart: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(weekStart * 1000))

/**
 * That week's gallery: a Grove-styled board of the week's photos and voice notes,
 * with a "Play montage" button to relive them as a story. Tap a tile to view a
 * photo full-size or play a voice note. Matches the Grove colour/type theme.
 */
@Composable
fun WeekGalleryDialog(
    week: ForestWeek,
    memories: List<MemoryItem>,
    vm: com.drp33.quietsignal.viewmodels.MemoriesViewModel,
    onPlayMontage: () -> Unit,
    onClose: () -> Unit,
) {
    var openItem by remember { mutableStateOf<MemoryItem?>(null) }
    val sorted = remember(memories) { memories.sortedByDescending { it.epoch } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Grove.Surface),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Grove.Surface, Grove.Bg)))) {
                Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = galleryLabel(week.weekStart),
                                fontFamily = Newsreader,
                                fontWeight = FontWeight.Medium,
                                fontSize = 23.sp,
                                color = Grove.Ink,
                            )
                            Text(
                                text = "${memories.size} ${if (memories.size == 1) "moment" else "moments"} this week",
                                fontFamily = NunitoSans,
                                fontSize = 13.sp,
                                color = Grove.InkSoft,
                            )
                        }
                        TextButton(onClick = onClose) {
                            Text(text = "✕", color = Grove.InkSoft, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onPlayMontage,
                        enabled = memories.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Grove.Foliage,
                            disabledContainerColor = Grove.FoliageRest,
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(text = "▶  Play montage", fontFamily = NunitoSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (sorted.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "🌿 A quiet week — no moments were shared.",
                                fontFamily = NunitoSans,
                                color = Grove.InkSoft,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(sorted, key = { it.objectName }) { item ->
                                GalleryTile(item = item, onClick = { openItem = item })
                            }
                        }
                    }
                }
            }
        }
    }

    openItem?.let { item ->
        GalleryItemDialog(item = item, vm = vm, onClose = { openItem = null })
    }
}

@Composable
private fun GalleryTile(item: MemoryItem, onClick: () -> Unit) {
    val tint = if (item.type == "photo") Grove.Photo else Grove.Voice
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Grove.Surface2),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.aspectRatio(1f).clickable { onClick() },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val image = item.image
            if (item.type == "photo" && image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "Photo from ${item.sender}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(tint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = if (item.type == "photo") "📷" else "🎤", fontSize = 34.sp)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "${item.sender} · ${groveAgo(item.epoch)}",
                    fontFamily = NunitoSans,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** Full-size photo, or a play control for a voice note — Grove themed. */
@Composable
private fun GalleryItemDialog(
    item: MemoryItem,
    vm: com.drp33.quietsignal.viewmodels.MemoriesViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { AudioPlayer(context) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(item.objectName) { onDispose { player.release() } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Grove.Surface),
            modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val image = item.image
                if (item.type == "photo" && image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = "Photo from ${item.sender}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Grove.Voice.copy(alpha = 0.16f))
                            .clickable {
                                if (playing) {
                                    player.pause(); playing = false
                                } else {
                                    vm.loadMediaBytes(item.objectName) { bytes ->
                                        player.play(bytes) { playing = false }
                                        playing = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = if (playing) "⏸" else "▶", fontSize = 56.sp, color = Grove.Voice)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${item.sender} · ${groveAgo(item.epoch)}",
                    fontFamily = NunitoSans,
                    color = Grove.InkSoft,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onClose) {
                    Text("Close", fontFamily = NunitoSans, fontWeight = FontWeight.Bold, color = Grove.Accent)
                }
            }
        }
    }
}

private fun groveAgo(epochSec: Long): String {
    val diff = System.currentTimeMillis() / 1000 - epochSec
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        else -> "${diff / 86400}d ago"
    }
}