package com.drp33.quietsignal.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.drp33.quietsignal.model.TreeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The shared "watering tree". Polled while the app is open so Norman and Sadie
 * always see the same tree (the server derives the stage from the joint history).
 */
class TreeViewModel(
    private val repository: CheckInRepository,
) : ViewModel() {

    var state by mutableStateOf(TreeState())
        private set

    init {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(3000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repository.getTree().onSuccess { state = it }
        }
    }

    /** Water the tree as [userId] (a check-in event), then refresh the shared state. */
    fun water(userId: Int) {
        viewModelScope.launch {
            repository.postSendOkay(userId)
                .onSuccess { refresh() }
                .onFailure { Log.e("Tree", "Watering failed", it) }
        }
    }
}
