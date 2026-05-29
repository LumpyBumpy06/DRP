package com.drp33.quietsignal.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.data.AdultState
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdultViewModel(
    private val repository: CheckInRepository
): ViewModel() {
    var state by mutableStateOf(AdultState())
        private set

    fun onRemoteTokenChange(newToken: String) {
        state = state.copy(
            remoteToken = newToken
        )
    }

    fun postFCMToken(userId: Int) {
        viewModelScope.launch {
            repository.postRegisterToken(userId, Firebase.messaging.token.await())
        }
    }

}