package com.drp33.quietsignal.services

import android.util.Log
import com.drp33.quietsignal.data.RolePreferences
import com.drp33.quietsignal.data.remote.RetroFitProvider
import com.drp33.quietsignal.data.remote.models.TokenRequest
import com.drp33.quietsignal.model.NotificationBus
import com.drp33.quietsignal.model.UserRole
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PushNotificationService: FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // FCM rotates tokens periodically. If we don't re-register the new one,
        // the backend keeps pushing to a dead token and THIS device silently stops
        // receiving notifications — which shows up as one-directional, flaky sync.
        // Re-register against the chosen role's user id (the role is persisted).
        val userId = when (RolePreferences.get(this)) {
            UserRole.NORMAN -> 1
            UserRole.SADIE -> 2
            null -> return // no role chosen yet; registered on next role-screen entry
        }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                RetroFitProvider.getCheckInAPI(applicationContext)
                    .postRegisterToken(TokenRequest(userId, token))
            }.onFailure { Log.e("PushService", "Failed to re-register FCM token", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.i("RAFIMISHA", "Notification received!!")

        // TODO Based on message data received from backend do some stuff.
        val type = message.data["type"]
        if (type == "CHECKED_IN" || type == "VOICE_MESSAGE" || type == "EMERGENCY" || type == "PHOTO_MESSAGE" || type == "THREAD_MESSAGE"){
            CoroutineScope(Dispatchers.IO).launch {
                NotificationBus.send(type)
            }
        }

    }
}