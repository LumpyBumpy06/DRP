package com.drp33.quietsignal.viewmodels

import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.drp33.quietsignal.model.MemoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loads the full memory board (every voice memo + snap ever sent) and decodes snap thumbnails. */
class MemoriesViewModel(
    private val repository: CheckInRepository,
) : ViewModel() {

    var memories by mutableStateOf<List<MemoryItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    fun load() {
        viewModelScope.launch {
            loading = true
            repository.getMemories()
                .onSuccess { items ->
                    memories = items
                    loading = false
                    // Decode each snap thumbnail in the background, filling tiles in as they arrive.
                    items.filter { it.type == "photo" }.forEach { item ->
                        repository.getMedia(item.objectName).onSuccess { bytes ->
                            val bitmap = withContext(Dispatchers.Default) {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
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

    /** Fetch a memory's raw bytes (e.g. to play a voice memo). */
    fun loadMediaBytes(objectName: String, onBytes: (ByteArray) -> Unit) {
        viewModelScope.launch {
            repository.getMedia(objectName).onSuccess(onBytes)
        }
    }
}
