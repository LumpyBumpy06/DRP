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

    private val fetching = mutableSetOf<String>()

    fun load(onlyMetadata: Boolean = false) {
        // We still allow a refresh if not already loading, but we'll be smart about the images.
        if (loading) return
        viewModelScope.launch {
            loading = true
            repository.getMemories()
                .onSuccess { items ->
                    // Immediately restore any bitmaps we already have in the session cache.
                    memories = items.map { 
                        it.copy(image = MediaCache.get(it.objectName))
                    }
                    loading = false
                    loadTags()
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

    /** Fetch a memory's raw bytes (e.g. to play a voice memo) or decode a thumbnail. */
    fun loadMediaBytes(objectName: String, onBytes: (ByteArray) -> Unit) {
        MediaCache.getBytes(objectName)?.let { onBytes(it); return }
        
        if (fetching.contains(objectName)) return
        fetching.add(objectName)

        viewModelScope.launch {
            try {
                repository.getMedia(objectName).onSuccess { bytes ->
                    MediaCache.putBytes(objectName, bytes)
                    onBytes(bytes)
                }
            } finally {
                fetching.remove(objectName)
            }
        }
    }

    /** Specifically ensures a photo memory has its thumbnail bitmap loaded. */
    fun loadThumbnail(item: MemoryItem) {
        if (item.type != "photo" || item.image != null) return
        
        // Check session cache first (bitmaps or raw bytes).
        val cachedBmp = MediaCache.get(item.objectName)
        if (cachedBmp != null) {
            memories = memories.map { if (it.objectName == item.objectName) it.copy(image = cachedBmp) else it }
            return
        }

        if (fetching.contains(item.objectName)) return
        fetching.add(item.objectName)

        viewModelScope.launch {
            try {
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
                        memories = memories.map { if (it.objectName == item.objectName) it.copy(image = bmp) else it }
                    }
                }
            } finally {
                fetching.remove(item.objectName)
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
