package com.drp33.quietsignal.viewmodels

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.data.repo.CheckInRepository
import com.drp33.quietsignal.model.ElderlyUIState
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ElderlyViewModel(
    private val repository: CheckInRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ElderlyUIState())
    val uiState = _uiState.asStateFlow()

    fun onOkayClick(userId: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.postSendOkay(userId)
                .onSuccess { onSuccess() }
                .onFailure { Log.e("Elderly", "API failed while sending poset send") }
        }
    }

    fun loadCheckIn(userId: Int, showLoading: Boolean = true){
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            repository.getOkayStatus(userId)
                .onSuccess { shouldShow ->
                    _uiState.value = ElderlyUIState(
                        isLoading = false,
                        showCheckIn = !shouldShow
                    )
                }
                .onFailure {
                    Log.e("Elderly", "API failed to give me get okay")
                    Log.e("Elderly", it.message ?: "unknown error")
                    // Keep the current view on a refresh failure; just stop the spinner.
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }

    fun postFCMToken(userId: Int) {
        viewModelScope.launch {
            repository.postRegisterToken(userId, Firebase.messaging.token.await())
        }
    }

}