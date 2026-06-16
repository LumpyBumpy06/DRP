package com.drp33.quietsignal.ui.screens

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drp33.quietsignal.model.MemoryItem
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.Newsreader
import com.drp33.quietsignal.ui.theme.NunitoSans
import com.drp33.quietsignal.util.AudioPlayer
import com.drp33.quietsignal.viewmodels.MemoriesViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ============================================================= *
 *  GALLERY  —  shared Grove-themed memory gallery used by both
 *  the home "Our memories" board and each forest week's gallery.
 *
 *  - Segmented filter:  All · Photos · Voice  (starts on All)
 *  - Tag filter chips:  filter to Favourites / Family / custom…
 *  - Photos:            a quilted masonry grid (real gallery feel)
 *  - Voice notes:       slim horizontal bars (play · waveform · time)
 *  - Tap any item:      full view + tagging + reshare / conversation
 *
 *  Tokens only (Grove.*, Newsreader, NunitoSans) — no off-palette
 *  colours, so it sits inside the rest of the app seamlessly.
 * ============================================================= */

const val FAVOURITE_TAG = "Favourites"

/** Tags we always offer in the editor, even before any exist on the server. */
val SUGGESTED_TAGS = listOf(FAVOURITE_TAG, "Family", "Funny", "Special")

private fun isFavourite(item: MemoryItem): Boolean = item.tags.any { it.equals(FAVOURITE_TAG, ignoreCase = true) }

/** All tag names offered in the editor: suggestions first, then any others that
 *  already exist on the server or on this memory (Favourites always leads). */
fun knownTagNames(serverTags: List<String>, current: List<String>): List<String> {
    val out = ArrayList<String>(SUGGESTED_TAGS)
    fun add(name: String) {
        val n = name.trim()
        if (n.isNotEmpty() && out.none { it.equals(n, ignoreCase = true) }) out.add(n)
    }
    serverTags.forEach { add(it) }
    current.forEach { add(it) }
    return out
}

/** Tags that actually appear on the given memories — the only ones worth
 *  offering as filter chips. Favourites is pinned first; the rest alphabetical. */
private fun filterableTags(memories: List<MemoryItem>): List<String> {
    val seen = LinkedHashMap<String, String>() // lower -> display
    memories.forEach { m -> m.tags.forEach { t ->
        val k = t.trim().lowercase()
        if (k.isNotEmpty() && !seen.containsKey(k)) seen[k] = t.trim()
    } }
    val all = seen.values.toMutableList()
    all.sortBy { it.lowercase() }
    val fav = all.firstOrNull { it.equals(FAVOURITE_TAG, ignoreCase = true) }
    if (fav != null) { all.remove(fav); all.add(0, fav) }
    return all
}

fun groveAgo(epochSec: Long): String {
    val diff = System.currentTimeMillis() / 1000 - epochSec
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        else -> "${diff / 86400}d ago"
    }
}

private fun galleryGroupTitle(epochSec: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochSec * 1000 }
    val now = Calendar.getInstance()
    val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay(cal, now) -> "Today"
        sameDay(cal, yest) -> "Yesterday"
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(epochSec * 1000))
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(epochSec * 1000))
    }
}

/* ----------------------------- the gallery body ----------------------------- */

/**
 * The scrollable gallery (segmented filter + tag chips + masonry/voice feed).
 * Hosting dialogs supply their own title bar above this. [groupByDate] adds
 * Today / Yesterday / date headers (used by the home board, not the per-week one).
 */
@Composable
fun GalleryBody(
    memories: List<MemoryItem>,
    vm: MemoriesViewModel,
    currentUserId: Int,
    onStartThread: ((MemoryItem, String) -> Unit)?,
    threadExistsFor: (MemoryItem) -> Boolean = { false },
    groupByDate: Boolean,
    showItemActions: Boolean,
    modifier: Modifier = Modifier,
) {
    var typeFilter by remember { mutableStateOf("all") } // all | photo | voice
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var openItem by remember { mutableStateOf<MemoryItem?>(null) }

    val tagChips = remember(memories) { filterableTags(memories) }
    // If the active tag filter disappears (last tagged item untagged), reset it.
    LaunchedEffect(tagChips) {
        if (tagFilter != null && tagChips.none { it.equals(tagFilter, ignoreCase = true) }) tagFilter = null
    }

    val sorted = remember(memories, typeFilter, tagFilter) {
        memories
            .filter { typeFilter == "all" || it.type == typeFilter }
            .filter { m -> tagFilter == null || m.tags.any { it.equals(tagFilter, ignoreCase = true) } }
            .sortedByDescending { it.epoch }
    }
    val grouped = remember(sorted, groupByDate) {
        if (!groupByDate) emptyMap() else LinkedHashMap<String, MutableList<MemoryItem>>().apply {
            sorted.forEach { getOrPut(galleryGroupTitle(it.epoch)) { mutableListOf() }.add(it) }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        GallerySegmented(filter = typeFilter, onChange = { typeFilter = it })

        if (tagChips.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            TagFilterRow(tags = tagChips, selected = tagFilter, onSelect = { tagFilter = it })
        }

        Spacer(Modifier.height(14.dp))

        if (sorted.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyMessage(typeFilter, tagFilter),
                    fontFamily = NunitoSans,
                    color = Grove.InkSoft,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                verticalItemSpacing = 10.dp,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (groupByDate) {
                    grouped.forEach { (title, items) ->
                        item(span = StaggeredGridItemSpan.FullLine, key = "h_$title") { GalleryDateHeader(title) }
                        items.forEach { m ->
                            if (m.type == "photo") {
                                item(key = m.objectName) { PhotoTile(m, vm) { openItem = m } }
                            } else {
                                item(span = StaggeredGridItemSpan.FullLine, key = m.objectName) { VoiceBar(m, vm) { openItem = m } }
                            }
                        }
                    }
                } else {
                    sorted.forEach { m ->
                        if (m.type == "photo") {
                            item(key = m.objectName) { PhotoTile(m, vm) { openItem = m } }
                        } else {
                            item(span = StaggeredGridItemSpan.FullLine, key = m.objectName) { VoiceBar(m, vm) { openItem = m } }
                        }
                    }
                }
            }
        }
    }

    openItem?.let { item ->
        if (item.type == "photo") {
            FullscreenPhotoViewer(
                item = item,
                vm = vm,
                currentUserId = currentUserId,
                onStartThread = onStartThread,
                hasThread = threadExistsFor(item),
                showItemActions = showItemActions,
                onClose = { openItem = null },
            )
        } else {
            MemoryDetailDialog(
                item = item,
                vm = vm,
                currentUserId = currentUserId,
                onStartThread = onStartThread,
                hasThread = threadExistsFor(item),
                showItemActions = showItemActions,
                onClose = { openItem = null },
            )
        }
    }
}

private fun emptyMessage(typeFilter: String, tagFilter: String?): String = when {
    tagFilter != null -> "No moments tagged “$tagFilter” yet."
    typeFilter == "photo" -> "No photos yet.\nShare a snap to start your story."
    typeFilter == "voice" -> "No voice notes yet.\nRecord one to start your story."
    else -> "No moments yet.\nShare a photo or a voice note to begin."
}

/* ----------------------------- segmented filter ----------------------------- */

@Composable
private fun GallerySegmented(filter: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Grove.Surface2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegmentButton("All", filter == "all", Modifier.weight(1f)) { onChange("all") }
        SegmentButton("Photos", filter == "photo", Modifier.weight(1f)) { onChange("photo") }
        SegmentButton("Voice", filter == "voice", Modifier.weight(1f)) { onChange("voice") }
    }
}

@Composable
private fun SegmentButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Grove.Surface else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = NunitoSans,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Grove.Ink else Grove.InkSoft,
        )
    }
}

/* ----------------------------- tag filter chips ----------------------------- */

@Composable
private fun TagFilterRow(tags: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(label = "All", icon = null, selected = selected == null) { onSelect(null) }
        tags.forEach { tag ->
            val fav = tag.equals(FAVOURITE_TAG, ignoreCase = true)
            FilterChip(
                label = tag,
                icon = if (fav) "★" else null,
                selected = selected?.equals(tag, ignoreCase = true) == true,
            ) { onSelect(if (selected?.equals(tag, ignoreCase = true) == true) null else tag) }
        }
    }
}

@Composable
private fun FilterChip(label: String, icon: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (selected) Grove.Accent else Grove.Surface)
            .border(1.dp, if (selected) Color.Transparent else Grove.Line, RoundedCornerShape(19.dp))
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Text(text = icon, fontSize = 13.sp, color = if (selected) Color.White else Grove.Accent)
        Text(
            text = label,
            fontFamily = NunitoSans,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Color.White else Grove.InkSoft,
        )
    }
}

/* ----------------------------- date header ----------------------------- */

@Composable
private fun GalleryDateHeader(title: String) {
    Text(
        text = title,
        fontFamily = Newsreader,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        color = Grove.Ink,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
    )
}

/* ----------------------------- photo tile (masonry) ----------------------------- */

@Composable
private fun PhotoTile(item: MemoryItem, vm: MemoriesViewModel, onOpen: () -> Unit) {
    val img = item.image
    val ratio = if (img != null && img.height > 0) (img.width.toFloat() / img.height).coerceIn(0.62f, 1.45f) else 1f
    val others = item.tags.filterNot { it.equals(FAVOURITE_TAG, ignoreCase = true) }

    LaunchedEffect(item.objectName) {
        vm.loadThumbnail(item)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Grove.Surface2),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(ratio)) {
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = "Photo from ${item.sender}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Grove.Photo.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Text(text = "🌿", fontSize = 28.sp) }
            }

            // Quick-favourite heart (top-right).
            FavouriteHeart(
                on = isFavourite(item),
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
            ) { toggleFavourite(item, vm) }

            // Caption + (optional) first tag along the bottom.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (others.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TilePill(others.first())
                        if (others.size > 1) TilePill("+${others.size - 1}")
                    }
                }
                Text(
                    text = "${item.sender} · ${groveAgo(item.epoch)}",
                    fontFamily = NunitoSans,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TilePill(text: String) {
    Text(
        text = text,
        fontFamily = NunitoSans,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0x3DFFFFFF))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun FavouriteHeart(on: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (on) Grove.Accent else Color(0x4D1C1A12))
            .clickable { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = if (on) "♥" else "♡", color = Color.White, fontSize = 15.sp)
    }
}

private fun toggleFavourite(item: MemoryItem, vm: MemoriesViewModel) {
    if (isFavourite(item)) {
        vm.removeTag(item.objectName, FAVOURITE_TAG)
    } else {
        vm.addTag(item.objectName, FAVOURITE_TAG)
    }
}

/* ----------------------------- voice bar (horizontal) ----------------------------- */

@Composable
private fun VoiceBar(item: MemoryItem, vm: MemoriesViewModel, onOpen: () -> Unit) {
    val context = LocalContext.current
    val player = remember(item.objectName) { AudioPlayer(context) }
    var playing by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    DisposableEffect(item.objectName) { onDispose { player.release() } }
    LaunchedEffect(playing) { while (playing) { positionMs = player.position(); delay(60) } }
    // Probe the clip's length once so the bar can show its duration before playing.
    LaunchedEffect(item.objectName) {
        vm.loadMediaBytes(item.objectName) { bytes ->
            if (durationMs == 0) durationMs = player.durationOf(bytes)
        }
    }

    val others = item.tags.filterNot { it.equals(FAVOURITE_TAG, ignoreCase = true) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Grove.Surface)
            .border(1.dp, Grove.Line, RoundedCornerShape(16.dp))
            .clickable { onOpen() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        // Play / pause.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Grove.Voice)
                .clickable {
                    if (playing) { player.pause(); playing = false }
                    else vm.loadMediaBytes(item.objectName) { bytes ->
                        durationMs = player.play(bytes) { playing = false; positionMs = 0 }
                        playing = true
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Text(text = if (playing) "❚❚" else "▶", color = Color.White, fontSize = if (playing) 13.sp else 16.sp, fontWeight = FontWeight.Bold) }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SeededWaveform(seed = item.objectName, color = Grove.Voice, playing = playing)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Voice note · ${item.sender} · ${groveAgo(item.epoch)}",
                    fontFamily = NunitoSans,
                    fontSize = 11.5.sp,
                    color = Grove.InkSoft,
                    maxLines = 1,
                )
                if (durationMs > 0) {
                    Text(
                        text = "· ${formatTime(if (playing) positionMs else durationMs)}",
                        fontFamily = NunitoSans,
                        fontSize = 11.5.sp,
                        color = Grove.InkFaint,
                        maxLines = 1,
                    )
                }
                if (others.isNotEmpty()) {
                    // Single line, ellipsised — never wraps a tag mid-word.
                    Text(
                        text = "· ${others.first()}${if (others.size > 1) " +${others.size - 1}" else ""}",
                        fontFamily = NunitoSans,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Grove.Voice,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable { toggleFavourite(item, vm) },
            contentAlignment = Alignment.Center,
        ) { Text(text = if (isFavourite(item)) "♥" else "♡", color = if (isFavourite(item)) Grove.Accent else Grove.InkFaint, fontSize = 18.sp) }
    }
}

/** A deterministic little waveform — its shape is seeded by the clip's name, so
 *  every voice note looks distinct (no two identical mic blocks). */
@Composable
fun SeededWaveform(seed: String, color: Color, playing: Boolean = false, bars: Int = 30, heightDp: Int = 26) {
    val heights = remember(seed, bars) {
        val rnd = java.util.Random(seed.hashCode().toLong())
        FloatArray(bars) { 0.22f + rnd.nextFloat() * 0.78f }
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(heightDp.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = if (playing) 0.95f else 0.5f)),
            )
        }
    }
}

private fun formatTime(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/* ----------------------------- detail dialog + tag editor ----------------------------- */

@Composable
private fun MemoryDetailDialog(
    item: MemoryItem,
    vm: MemoriesViewModel,
    currentUserId: Int,
    onStartThread: ((MemoryItem, String) -> Unit)?,
    hasThread: Boolean,
    showItemActions: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var showCaption by remember { mutableStateOf(false) }
    val player = remember { AudioPlayer(context) }
    var playing by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(playing) { while (playing) { positionMs = player.position(); delay(60) } }

    // Live copy of this memory so tag edits redraw the sheet immediately.
    val live = vm.memories.firstOrNull { it.objectName == item.objectName } ?: item
    val known = remember(vm.allTags, live.tags) { knownTagNames(vm.allTags, live.tags) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Grove.Surface),
            modifier = Modifier.fillMaxWidth(0.92f).padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (live.type == "photo") {
                    val img = live.image
                    if (img != null) {
                        Image(
                            bitmap = img,
                            contentDescription = "Photo from ${live.sender}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(16.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Grove.Photo.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(text = "🌿", fontSize = 40.sp) }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Grove.Voice.copy(alpha = 0.12f))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Grove.Voice)
                                .clickable {
                                    if (playing) { player.pause(); playing = false }
                                    else vm.loadMediaBytes(live.objectName) { bytes ->
                                        durationMs = player.play(bytes) { playing = false; positionMs = 0 }
                                        playing = true
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(text = if (playing) "❚❚" else "▶", color = Color.White, fontSize = if (playing) 16.sp else 20.sp, fontWeight = FontWeight.Bold) }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SeededWaveform(seed = live.objectName, color = Grove.Voice, playing = playing, heightDp = 34)
                            Text(
                                text = if (durationMs > 0) "${formatTime(positionMs)} / ${formatTime(durationMs)}" else "Tap to play",
                                fontFamily = NunitoSans, fontSize = 12.sp, color = Grove.InkSoft,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${live.sender} · ${groveAgo(live.epoch)}",
                    fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft,
                )

                Spacer(Modifier.height(16.dp))
                TagEditor(
                    current = live.tags,
                    known = known,
                    onToggle = { tag ->
                        val on = live.tags.any { it.equals(tag, ignoreCase = true) }
                        if (on) vm.removeTag(live.objectName, tag) else vm.addTag(live.objectName, tag)
                    },
                    onCreate = { tag -> vm.addTag(live.objectName, tag) },
                )

                if (showItemActions) {
                    Spacer(Modifier.height(18.dp))
                    if (onStartThread != null) {
                        Button(
                            // An existing chat opens straight away; a new one asks for a title first.
                            onClick = { if (hasThread) { onStartThread(live, ""); onClose() } else showCaption = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Grove.Foliage),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) { Text(if (hasThread) "Continue conversation" else "Start a conversation", fontFamily = NunitoSans, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp) }
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                vm.reshare(live, currentUserId) {
                                    Toast.makeText(context, "Shared again 🌱", Toast.LENGTH_SHORT).show(); onClose()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Grove.Surface2, contentColor = Grove.Ink),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("Reshare", fontFamily = NunitoSans, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        Button(
                            onClick = { vm.loadMediaBytes(live.objectName) { bytes -> saveMemoryToDisk(context, bytes, live.type, live.objectName) } },
                            colors = ButtonDefaults.buttonColors(containerColor = Grove.Surface2, contentColor = Grove.Ink),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("Save", fontFamily = NunitoSans, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Close", fontFamily = NunitoSans, fontWeight = FontWeight.Bold, color = Grove.Accent, fontSize = 15.sp)
                }
            }
        }
    }

    if (showCaption && onStartThread != null) {
        CaptionDialog(
            onConfirm = { caption -> onStartThread(live, caption); showCaption = false; onClose() },
            onDismiss = { showCaption = false },
        )
    }
}

/* ----------------------------- fullscreen photo viewer ----------------------------- */

/**
 * Tapping a photo opens it full-screen over a dim backdrop. A light, transparent
 * action bar sits along the bottom: Tags (a popup of the tag editor floats just
 * above it), Reshare (middle), and Start a conversation (right — which asks for a
 * caption that becomes the conversation's title). Grove tokens throughout.
 */
@Composable
private fun FullscreenPhotoViewer(
    item: MemoryItem,
    vm: MemoriesViewModel,
    currentUserId: Int,
    onStartThread: ((MemoryItem, String) -> Unit)?,
    hasThread: Boolean,
    showItemActions: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val live = vm.memories.firstOrNull { it.objectName == item.objectName } ?: item
    val known = remember(vm.allTags, live.tags) { knownTagNames(vm.allTags, live.tags) }
    var showTags by remember { mutableStateOf(false) }
    var showCaption by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xF21C1A12))) {
            val img = live.image
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = "Photo from ${live.sender}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(vertical = 72.dp),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("🌿", fontSize = 56.sp) }
            }

            // Close + who/when (top).
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = Color.White, fontSize = 17.sp) }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${live.sender} · ${groveAgo(live.epoch)}",
                    fontFamily = NunitoSans, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f),
                )
            }

            // Tag editor popup, floating just above the bottom bar.
            if (showTags) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Grove.Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 82.dp)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp).heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        TagEditor(
                            current = live.tags,
                            known = known,
                            onToggle = { tag ->
                                val on = live.tags.any { it.equals(tag, ignoreCase = true) }
                                if (on) vm.removeTag(live.objectName, tag) else vm.addTag(live.objectName, tag)
                            },
                            onCreate = { tag -> vm.addTag(live.objectName, tag) },
                        )
                    }
                }
            }

            // Bottom action bar: Tags · Reshare · Start a conversation.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassBarButton(icon = "🏷", label = "Tags", selected = showTags, modifier = Modifier.weight(1f)) { showTags = !showTags }
                GlassBarButton(icon = "⤓", label = "Save", modifier = Modifier.weight(1f)) {
                    vm.loadMediaBytes(live.objectName) { bytes -> saveMemoryToDisk(context, bytes, live.type, live.objectName) }
                }
                if (showItemActions) {
                    GlassBarButton(icon = "↻", label = "Reshare", modifier = Modifier.weight(1f)) {
                        vm.reshare(live, currentUserId) {
                            Toast.makeText(context, "Shared again 🌱", Toast.LENGTH_SHORT).show(); onClose()
                        }
                    }
                    if (onStartThread != null) {
                        GlassBarButton(
                            icon = "💬",
                            label = if (hasThread) "Continue conversation" else "Start a conversation",
                            modifier = Modifier.weight(1f),
                        ) { if (hasThread) { onStartThread(live, ""); onClose() } else showCaption = true }
                    }
                }
            }
        }
    }

    if (showCaption && onStartThread != null) {
        CaptionDialog(
            onConfirm = { caption -> onStartThread(live, caption); showCaption = false; onClose() },
            onDismiss = { showCaption = false },
        )
    }
}

/** A light, transparent action (icon over a wrapping label) used along the
 *  fullscreen photo's bottom bar — vertical so longer labels stay readable. */
@Composable
private fun GlassBarButton(icon: String, label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Column(
        // Fixed height so every button in the bar is the same size, whether its
        // label fits on one line or two.
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x40FFFFFF) else Color(0x1FFFFFFF))
            .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Text(text = icon, fontSize = 16.sp, color = Color.White)
        Text(
            text = label,
            fontFamily = NunitoSans,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * A simple centred popup that asks for a caption before starting a conversation.
 * The caption becomes the thread's title throughout the app.
 */
@Composable
private fun CaptionDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var caption by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Grove.Surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("Start a conversation", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 21.sp, color = Grove.Ink)
                Spacer(Modifier.height(4.dp))
                Text("Add a caption to title this conversation.", fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Add a caption…", fontFamily = NunitoSans, color = Grove.InkFaint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel", fontFamily = NunitoSans, fontWeight = FontWeight.Bold, color = Grove.InkSoft, fontSize = 15.sp)
                    }
                    Button(
                        onClick = { onConfirm(caption.trim()) },
                        enabled = caption.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Grove.Foliage, disabledContainerColor = Grove.FoliageRest),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) { Text("Start", fontFamily = NunitoSans, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(
    current: List<String>,
    known: List<String>,
    onToggle: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    var newTag by remember { mutableStateOf("") }

    Text(text = "Tags", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Grove.Ink)
    Spacer(Modifier.height(3.dp))
    Text(text = "Tap to add or remove. Tag a moment so it's easy to find later.", fontFamily = NunitoSans, fontSize = 12.5.sp, color = Grove.InkSoft)
    Spacer(Modifier.height(10.dp))

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        known.forEach { tag ->
            val on = current.any { it.equals(tag, ignoreCase = true) }
            EditableTagChip(label = tag, on = on, fav = tag.equals(FAVOURITE_TAG, ignoreCase = true)) { onToggle(tag) }
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = newTag,
            onValueChange = { newTag = it },
            placeholder = { Text("New tag…", fontFamily = NunitoSans, color = Grove.InkFaint) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { val t = newTag.trim(); if (t.isNotEmpty()) { onCreate(t); newTag = "" } },
            enabled = newTag.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Grove.Accent, disabledContainerColor = Grove.FoliageRest),
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier.height(52.dp),
        ) { Text("Add", fontFamily = NunitoSans, fontWeight = FontWeight.Bold, color = Color.White) }
    }
}

@Composable
private fun EditableTagChip(label: String, on: Boolean, fav: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(if (on) Grove.Accent else Grove.Surface2)
            .border(1.dp, if (on) Color.Transparent else Grove.Line, RoundedCornerShape(21.dp))
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (on) "✓" else if (fav) "★" else "+",
            fontSize = 13.sp,
            color = if (on) Color.White else if (fav) Grove.Accent else Grove.InkFaint,
        )
        Text(
            text = label,
            fontFamily = NunitoSans,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (on) Color.White else Grove.Ink,
        )
    }
}

/* ----------------------------- helpers ----------------------------- */

private fun saveMemoryToDisk(context: Context, bytes: ByteArray, type: String, objectName: String) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, objectName.substringAfterLast("/"))
        put(MediaStore.MediaColumns.MIME_TYPE, if (type == "photo") "image/jpeg" else "audio/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val folder = if (type == "photo") Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MUSIC
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Grove")
        }
    }
    val uri = if (type == "photo") {
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    } else {
        resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
    }
    uri?.let {
        resolver.openOutputStream(it)?.use { os ->
            os.write(bytes)
            Toast.makeText(context, "Saved to ${if (type == "photo") "Photos" else "Music"}", Toast.LENGTH_SHORT).show()
        }
    } ?: Toast.makeText(context, "Couldn't save", Toast.LENGTH_SHORT).show()
}
