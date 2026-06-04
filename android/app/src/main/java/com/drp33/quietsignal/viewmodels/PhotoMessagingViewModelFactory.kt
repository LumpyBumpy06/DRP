package com.drp33.quietsignal.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.drp33.quietsignal.data.repo.CheckInRepository

class PhotoMessagingViewModelFactory(
    private val repository: CheckInRepository,
    private val selfId: Int,
    private val peerId: Int,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PhotoMessagingViewModel(repository, selfId, peerId) as T
    }
}
