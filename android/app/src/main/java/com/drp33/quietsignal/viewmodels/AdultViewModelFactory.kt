package com.drp33.quietsignal.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.drp33.quietsignal.data.repo.CheckInRepository

class AdultViewModelFactory(private val repository: CheckInRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AdultViewModel(repository) as T
    }
}