package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drp33.quietsignal.model.MemoryItem
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.Newsreader
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.viewmodels.MemoriesViewModel

/**
 * "Our memories" — the shared board of every photo and voice note. Grove-themed
 * to match the rest of the app: a warm surface, a serif title, a segmented
 * All · Photos · Voice filter, tag-filter chips, a quilted photo gallery and
 * slim voice bars. Tap a moment to view it, tag it, reshare it, or start a chat.
 */
@Composable
fun MemoriesDialog(
    vm: MemoriesViewModel,
    currentUserId: Int,
    onClose: () -> Unit,
    onStartThread: ((MemoryItem) -> Unit)? = null,
) {
    LaunchedEffect(Unit) { vm.load() }

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
                                text = "Our memories",
                                fontFamily = Newsreader,
                                fontWeight = FontWeight.Medium,
                                fontSize = 27.sp,
                                color = Grove.Ink,
                            )
                            Text(
                                text = "Every moment, growing together.",
                                fontFamily = NunitoSans,
                                fontSize = 13.sp,
                                color = Grove.InkSoft,
                            )
                        }
                        GroveCircleButton(glyph = "✕", contentDescription = "Close", onClick = onClose)
                    }

                    Spacer(Modifier.height(16.dp))

                    if (vm.loading && vm.memories.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Grove.Foliage)
                        }
                    } else {
                        GalleryBody(
                            memories = vm.memories,
                            vm = vm,
                            currentUserId = currentUserId,
                            onStartThread = onStartThread,
                            groupByDate = true,
                            showItemActions = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
