package com.drp33.quietsignal.viewmodels

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
                }
            }
        }
    }

    fun loadInitialState(userId: Int) {
        viewModelScope.launch {
            repository.getOkayStatus(userId)
                .onSuccess { checkedIn ->
                    state = state.copy(checkedIn = checkedIn)
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