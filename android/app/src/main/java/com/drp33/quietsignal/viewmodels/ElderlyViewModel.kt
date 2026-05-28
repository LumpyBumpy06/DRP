package com.drp33.quietsignal.viewmodels

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drp33.quietsignal.data.repo.CheckInRepository
import kotlinx.coroutines.launch

class ElderlyViewModel(
    private val repository: CheckInRepository
) : ViewModel() {
    fun onOkayClick(userId: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.postSendOkay(userId)
                .onSuccess { onSuccess() }
                .onFailure { Log.e("Elderly", "API failed while sending poset send") }
        }
    }
}