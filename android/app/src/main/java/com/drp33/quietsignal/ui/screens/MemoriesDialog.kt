package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.model.MemoryItem
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.Newsreader
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.viewmodels.MemoriesViewModel

/**
 * "Our memories" — the shared board of every photo and voice note, shown as the
 * Gallery tab inside [MainShell] (the bottom nav stays visible, like the other
 * tabs): a warm gradient backdrop, a serif title, a segmented All · Photos ·
 * Voice filter, tag-filter chips, a quilted photo gallery and slim voice bars.
 * Tap a moment to view it, tag it, reshare it, or start a chat.
 */
@Composable
fun MemoriesScreen(
    vm: MemoriesViewModel,
    currentUserId: Int,
    contentPadding: PaddingValues = PaddingValues(),
    onStartThread: ((MemoryItem, String) -> Unit)? = null,
    threadExistsFor: (MemoryItem) -> Boolean = { false },
) {
    LaunchedEffect(Unit) { vm.load() }

    Box(modifier = Modifier.fillMaxSize().groveBackground()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            // Header matching the other tabs (Threads/Forest) — no back button.
            Column(modifier = Modifier.fillMaxWidth().padding(start = 2.dp)) {
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
                    threadExistsFor = threadExistsFor,
                    groupByDate = true,
                    showItemActions = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
