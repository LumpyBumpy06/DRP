package com.drp33.quietsignal.services

import com.drp33.quietsignal.model.NotificationBus
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PushNotificationService: FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // TODO Need to send token to backend to save in database
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // TODO Based on message data received from backend do some stuff.
        val type = message.data["type"]
        if (type == "CHECKED_IN"){
            CoroutineScope(Dispatchers.IO).launch {
                NotificationBus.send("CHECKED_IN")
            }
        }

    }
}