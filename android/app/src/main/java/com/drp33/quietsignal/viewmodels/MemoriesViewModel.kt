package com.drp33.quietsignal.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.drp33.quietsignal.model.ForestWeek
import com.drp33.quietsignal.model.MemoryItem
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
        viewModelScope.launch {
            loading = true
            repository.getMemories()
                .onSuccess { items ->
                    memories = items
                    loading = false
                    loadTags()
                    // Decode each snap thumbnail in the background, filling tiles in as they arrive.
                    items.filter { it.type == "photo" }.forEach { item ->
                        repository.getMedia(item.objectName).onSuccess { bytes ->
                            val bitmap = withContext(Dispatchers.Default) {
                                decodeSampledBitmap(bytes, 400, 400)?.asImageBitmap()
                            }
                            if (bitmap != null) {
                                memories = memories.map {
                                    if (it.objectName == item.objectName) it.copy(image = bitmap) else it
                                }
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
     * Replace the tag set on one memory. Updates the board optimistically so the
     * UI reacts at once, then persists to the server (shared with the partner)
     * and refreshes the known-tag list so any newly-created tag appears in the
     * filter chips.
     */
    fun setTags(objectName: String, tags: List<String>) {
        memories = memories.map { if (it.objectName == objectName) it.copy(tags = tags) else it }
        viewModelScope.launch {
            repository.setMemoryTags(objectName, tags).onSuccess { saved ->
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
        viewModelScope.launch {
            repository.getMedia(objectName).onSuccess(onBytes)
        }
    }

    /** Reshare a memory by re-posting it as the current user. */
    fun reshare(item: MemoryItem, currentUserId: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.getMedia(item.objectName).onSuccess { bytes ->
                val result = if (item.type == "photo") {
                    repository.postPhoto(currentUserId, bytes)
                } else {
                    repository.postVoice(currentUserId, bytes)
                }
                result.onSuccess { onComplete() }
            }
        }
    }
}
