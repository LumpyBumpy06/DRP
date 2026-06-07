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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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
                    // Norman tapping okay or leaving a voice note both mean "checked in".
                    "CHECKED_IN", "VOICE_MESSAGE" -> {
                        state = state.copy(checkedIn = true)
                    }
                    // Norman pressed the SOS button — surface the emergency popup.
                    "EMERGENCY" -> {
                        state = state.copy(emergency = true)
                    }
                }
            }
        }
    }

    /**
     * Poll-side fallback: surface an emergency that the push may have missed
     * (app backgrounded, arrived via the notification, app was killed, etc.).
     * Only ever raises the popup — clearing is the carer's explicit job.
     */
    fun loadEmergencyStatus(userId: Int) {
        viewModelScope.launch {
            repository.getEmergencyStatus(userId)
                .onSuccess { active -> if (active) state = state.copy(emergency = true) }
        }
    }

    /** Sadie tapped "All good" — close the popup and clear it on the server so it stops re-polling. */
    fun acknowledgeEmergency(userId: Int) {
        state = state.copy(emergency = false)
        viewModelScope.launch {
            repository.ackEmergency(userId)
                .onFailure { Log.e("Adult", "Failed to acknowledge emergency", it) }
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
            try {
                val token = withContext(Dispatchers.IO) {
                    Firebase.messaging.token.await()
                }
                repository.postRegisterToken(userId, token)
            } catch (e: Exception) {
                Log.e("Adult", "Failed to get FCM token", e)
            }
        }
    }

}
