package com.drp33.quietsignal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.drp33.quietsignal.viewmodels.PhotoMessagingViewModel
import java.io.ByteArrayOutputStream

/**
 * Tap to take a "snap". Requests the camera permission *at tap time*, then opens
 * the camera and hands back the captured photo as JPEG bytes.
 */
@Composable
fun SnapButton(onCaptured: (ByteArray) -> Unit, size: Dp = 96.dp) {
    val context = LocalContext.current

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            onCaptured(stream.toByteArray())
        }
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePicture.launch(null)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) takePicture.launch(null) else requestCamera.launch(Manifest.permission.CAMERA)
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A65)),
            modifier = Modifier.size(size),
        ) {
            Text(text = "📷", fontSize = 40.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Snap", style = MaterialTheme.typography.bodyMedium)
    }
}

/** The peer's latest snap, in a rounded card that springs in; tap to view full. */
@Composable
fun IncomingPhotoSection(peerName: String, vm: PhotoMessagingViewModel) {
    val image = vm.state.image ?: return
    var expanded by remember { mutableStateOf(false) }

    val appear = remember(image) { Animatable(0.82f) }
    LaunchedEffect(image) {
        appear.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
            },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📸 Snap from $peerName",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.clear() }) { Text("✕") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                bitmap = image,
                contentDescription = "Snap from $peerName",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { expanded = true },
            )
        }
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Image(
                bitmap = image,
                contentDescription = "Snap from $peerName",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { expanded = false },
            )
        }
    }
}
