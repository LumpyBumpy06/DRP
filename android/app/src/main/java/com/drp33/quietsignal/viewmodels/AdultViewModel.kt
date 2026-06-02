package com.drp33.quietsignal.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.model.AdultState
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.drp33.quietsignal.model.NotificationBus
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdultViewModel(
    private val repository: CheckInRepository
): ViewModel() {
    var state by mutableStateOf(AdultState())
        private set

    init {
        observeNotification()
    }

    private fun observeNotification(){
        viewModelScope.launch {
            NotificationBus.events.collect { event ->
                when (event){
                    "CHECKED_IN" -> {
                        state = state.copy(checkedIn = true)
                    }
                    "VOICE_MESSAGE" -> {
                        // A voice message is also a check-in.
                        state = state.copy(checkedIn = true, hasNewVoice = true)
                    }
                }
            }
        }
    }

    fun playLatestVoice(normanId: Int, play: (ByteArray) -> Unit) {
        viewModelScope.launch {
            state = state.copy(voiceStatus = "Loading…")
            repository.getLatestVoice(normanId)
                .onSuccess { bytes ->
                    state = state.copy(voiceStatus = "Playing", hasNewVoice = false)
                    play(bytes)
                }
                .onFailure {
                    Log.e("Adult", "Failed to fetch voice message", it)
                    // 404 also means the message has expired (past the day window).
                    state = state.copy(voiceStatus = "No message right now", hasNewVoice = false)
                }
        }
    }

    fun loadInitialState(userId: Int) {
        viewModelScope.launch {
            repository.getOkayStatus(userId)
                .onSuccess { checkedIn ->
                    state = if (checkedIn) {
                        state.copy(checkedIn = true)
                    } else {
                        // Day window passed: reset check-in and clear the pending voice.
                        state.copy(checkedIn = false, hasNewVoice = false, voiceStatus = "")
                    }
                }
                .onFailure {
                    state = state.copy(checkedIn = false)
                }
        }
    }

    fun postFCMToken(userId: Int) {
        viewModelScope.launch {
            repository.postRegisterToken(userId, Firebase.messaging.token.await())
        }
    }

}