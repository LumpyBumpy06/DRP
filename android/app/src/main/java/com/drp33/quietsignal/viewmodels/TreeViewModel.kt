package com.drp33.quietsignal.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.drp33.quietsignal.model.TreeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Polls the shared motivation-tree state while the screen is on view. */
class TreeViewModel(
    private val repository: CheckInRepository,
) : ViewModel() {

    var state by mutableStateOf(TreeState())
        private set

    init {
        viewModelScope.launch {
            while (true) {
                repository.getTree().onSuccess { state = it }
                delay(4000)
            }
        }
    }
}
