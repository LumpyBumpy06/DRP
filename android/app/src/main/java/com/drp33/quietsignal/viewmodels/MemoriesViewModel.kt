package com.drp33.quietsignal.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.drp33.quietsignal.model.ForestWeek
import com.drp33.quietsignal.model.MemoryItem
import com.drp33.quietsignal.util.MediaCache
import com.drp33.quietsignal.util.decodeSampledBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loads the full memory board (every voice memo + snap ever sent) and decodes snap thumbnails. */
class MemoriesViewModel(
    private val repository: CheckInRepository,
) : ViewModel() {

    var memories by mutableStateOf<List<MemoryItem>>(emptyList())
        private set
    var forestWeeks by mutableStateOf<List<ForestWeek>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    /** Every tag name known to the server — drives the gallery filter chips. */
    var allTags by mutableStateOf<List<String>>(emptyList())
        private set

    fun load() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            repository.getMemories()
                .onSuccess { items ->
                    memories = items
                    loading = false
                    loadTags()
                    // Decode snapshots in parallel batches of 5 to avoid network/CPU saturation
                    // while still providing a responsive "filling in" effect.
                    val allNewBitmaps = mutableMapOf<String, ImageBitmap>()
                    items.filter { it.type == "photo" }.chunked(5).forEach { chunk ->
                        val bitmaps = chunk.mapNotNull { item ->
                            // 1. Check bitmap cache
                            MediaCache.get(item.objectName)?.let { return@mapNotNull item.objectName to it }

                            // 2. Check bytes cache, or fetch from network
                            val bytes = MediaCache.getBytes(item.objectName)
                                ?: repository.getMedia(item.objectName).getOrNull()?.also {
                                    MediaCache.putBytes(item.objectName, it)
                                }

                            if (bytes != null) {
                                val bmp = withContext(Dispatchers.Default) {
                                    decodeSampledBitmap(bytes, 400, 400)?.asImageBitmap()
                                }
                                if (bmp != null) {
                                    MediaCache.put(item.objectName, bmp)
                                    item.objectName to bmp
                                } else null
                            } else null
                        }.toMap()

                        if (bitmaps.isNotEmpty()) {
                            allNewBitmaps.putAll(bitmaps)
                            memories = memories.map {
                                allNewBitmaps[it.objectName]?.let { bmp -> it.copy(image = bmp) } ?: it
                            }
                        }
                    }
                }
                .onFailure { loading = false }
        }
    }

    /** Refresh the list of every tag name (for the filter chips). */
    fun loadTags() {
        viewModelScope.launch {
            repository.getAllTags().onSuccess { allTags = it }
        }
    }

    /**
     * Add one tag to a memory. Optimistic locally, atomic on the server — the
     * server only ever inserts this single tag, so a stale local list can never
     * wipe out tags added earlier (or by the partner).
     */
    fun addTag(objectName: String, tag: String) {
        memories = memories.map {
            if (it.objectName == objectName && it.tags.none { t -> t.equals(tag, ignoreCase = true) }) {
                it.copy(tags = it.tags + tag)
            } else {
                it
            }
        }
        viewModelScope.launch {
            repository.addMemoryTag(objectName, tag).onSuccess { saved ->
                memories = memories.map { if (it.objectName == objectName) it.copy(tags = saved) else it }
                loadTags()
            }
        }
    }

    /** Remove one tag from a memory — atomic on the server, like [addTag]. */
    fun removeTag(objectName: String, tag: String) {
        memories = memories.map {
            if (it.objectName == objectName) {
                it.copy(tags = it.tags.filterNot { t -> t.equals(tag, ignoreCase = true) })
            } else {
                it
            }
        }
        viewModelScope.launch {
            repository.removeMemoryTag(objectName, tag).onSuccess { saved ->
                memories = memories.map { if (it.objectName == objectName) it.copy(tags = saved) else it }
                loadTags()
            }
        }
    }

    /** Load the per-week frozen tree snapshots for the forest. */
    fun loadForest() {
        viewModelScope.launch {
            repository.getForest().onSuccess { forestWeeks = it }
        }
    }

    /** Fetch a memory's raw bytes (e.g. to play a voice memo). */
    fun loadMediaBytes(objectName: String, onBytes: (ByteArray) -> Unit) {
        MediaCache.getBytes(objectName)?.let { onBytes(it); return }
        viewModelScope.launch {
            repository.getMedia(objectName).onSuccess { bytes ->
                MediaCache.putBytes(objectName, bytes)
                onBytes(bytes)
            }
        }
    }

    /** Reshare a memory: the server re-delivers it to the partner (popup +
     * notification) WITHOUT adding a duplicate to the gallery. */
    fun reshare(item: MemoryItem, currentUserId: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.reshare(currentUserId, item.objectName).onSuccess { onComplete() }
        }
    }
}
