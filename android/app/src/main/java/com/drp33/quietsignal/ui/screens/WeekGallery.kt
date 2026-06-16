package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drp33.quietsignal.model.ForestWeek
import com.drp33.quietsignal.model.MemoryItem
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.Newsreader
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun galleryLabel(weekStart: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(weekStart * 1000))

/**
 * That week's gallery — the same Grove-themed gallery used on the home board
 * (All · Photos · Voice filter, tag chips, quilted photos, voice bars), with a
 * "Play montage" button on top to relive the week as a story.
 */
@Composable
fun WeekGalleryDialog(
    week: ForestWeek,
    memories: List<MemoryItem>,
    vm: MemoriesViewModel,
    onPlayMontage: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Grove.Surface),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Grove.Surface, Grove.Bg)))) {
                Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = galleryLabel(week.weekStart),
                                fontFamily = Newsreader,
                                fontWeight = FontWeight.Medium,
                                fontSize = 24.sp,
                                color = Grove.Ink,
                            )
                            Text(
                                text = "${memories.size} ${if (memories.size == 1) "moment" else "moments"} this month",
                                fontFamily = NunitoSans,
                                fontSize = 13.sp,
                                color = Grove.InkSoft,
                            )
                        }
                        GroveCircleButton(glyph = "✕", contentDescription = "Close", onClick = onClose)
                    }

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = onPlayMontage,
                        enabled = memories.isNotEmpty(),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Grove.Foliage,
                            disabledContainerColor = Grove.FoliageRest,
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(text = "▶   Play montage", fontFamily = NunitoSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    }

                    Spacer(Modifier.height(16.dp))

                    GalleryBody(
                        memories = memories,
                        vm = vm,
                        currentUserId = 0,
                        onStartThread = null,
                        groupByDate = false,
                        showItemActions = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
