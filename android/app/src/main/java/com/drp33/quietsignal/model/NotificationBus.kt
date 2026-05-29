package com.drp33.quietsignal.model

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NotificationBus {
    private val _events = MutableSharedFlow<String>()

    val events = _events.asSharedFlow()

    suspend fun send(event: String) {
        _events.emit(event)
    }

}