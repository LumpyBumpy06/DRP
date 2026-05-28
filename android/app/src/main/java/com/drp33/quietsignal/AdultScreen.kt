package com.drp33.quietsignal

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AdultScreen(){

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Button(
            onClick = {
                scope.launch {
                    val token = Firebase.messaging.token.await()
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("FCM Token", token)))
                }
                Toast.makeText(context, "Copied token", Toast.LENGTH_SHORT).show();
            }
        ) {
            Text("copy token")
        }
    }
}