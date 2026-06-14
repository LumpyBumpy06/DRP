package com.drp33.quietsignal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.drp33.quietsignal.MainActivity
import com.drp33.quietsignal.R
import com.drp33.quietsignal.data.remote.RetroFitProvider
import com.drp33.quietsignal.data.repo.CheckInRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TreeWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.tree_widget_layout)
        
        // Create an Intent to launch MainActivity
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.tree_widget_container, pendingIntent)

        // Initial state or loading
        views.setTextViewText(R.id.widget_tree_status, "Updating...")
        appWidgetManager.updateAppWidget(appWidgetId, views)

        val repository = CheckInRepositoryImpl(RetroFitProvider.getCheckInAPI(context))
        
        CoroutineScope(Dispatchers.IO).launch {
            repository.getTree().onSuccess { state ->
                val emoji = getTreeEmoji(state.stage)
                val statusText = getTreeStatus(state.deathLevel)
                val statusColor = getStatusColor(state.deathLevel)
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                views.setTextViewText(R.id.widget_tree_emoji, emoji)
                views.setTextViewText(R.id.widget_tree_status, statusText)
                views.setTextColor(R.id.widget_tree_status, statusColor)
                views.setTextViewText(R.id.widget_update_time, "Updated: $time")
                
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }.onFailure {
                views.setTextViewText(R.id.widget_tree_status, "Offline")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun getTreeEmoji(stage: Int): String {
        return when {
            stage >= 5 -> "🌳"
            stage >= 2 -> "🌳" 
            stage >= 1 -> "🌿"
            else -> "🌱"
        }
    }

    private fun getTreeStatus(deathLevel: Float): String {
        return when {
            deathLevel >= 0.66f -> "Fading"
            deathLevel >= 0.33f -> "Okay"
            else -> "Thriving"
        }
    }

    private fun getStatusColor(deathLevel: Float): Int {
        return when {
            deathLevel >= 0.66f -> 0xFFD32F2F.toInt() // Red
            deathLevel >= 0.33f -> 0xFFFBC02D.toInt() // Yellow/Amber
            else -> 0xFF2E7D32.toInt() // Green
        }
    }
}
