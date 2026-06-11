package com.drp33.quietsignal.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.drp33.quietsignal.data.repo.CheckInRepository

class ThreadsViewModelFactory(
    private val repository: CheckInRepository,
    private val selfId: Int,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ThreadsViewModel(repository, selfId) as T
    }
}
