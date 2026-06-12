package com.drp33.quietsignal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.drp33.quietsignal.viewmodels.PhotoMessagingViewModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

/**
 * Tap to take a "snap". Opens a custom camera dialog with an integrated
 * gallery shortcut in the bottom left, mimicking a native camera app.
 */
@Composable
fun SnapButton(onCaptured: (ByteArray) -> Unit, size: Dp = 96.dp) {
    val context = LocalContext.current
    var showCamera by remember { mutableStateOf(false) }

    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCamera = true
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) showCamera = true else requestCamera.launch(Manifest.permission.CAMERA)
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

    if (showCamera) {
        CameraCaptureDialog(
            onCaptured = {
                onCaptured(it)
                showCamera = false
            },
            onClose = { showCamera = false }
        )
    }
}

/**
 * A full-screen camera interface with a live preview, a shutter button,
 * and a gallery shortcut in the bottom left.
 */
@Composable
fun CameraCaptureDialog(onCaptured: (ByteArray) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    var capturedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Launcher for the gallery shortcut
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val jpeg = runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@runCatching null
                // Bake the EXIF orientation into the pixels — downstream decoders
                // ignore EXIF, so an unrotated upload would display sideways.
                val exifRotation = when (
                    ExifInterface(raw.inputStream())
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                val upright = if (exifRotation != 0f) {
                    val matrix = Matrix().apply { postRotate(exifRotation) }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }
                val stream = ByteArrayOutputStream()
                upright.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                stream.toByteArray()
            }.getOrNull()
            if (jpeg != null) onCaptured(jpeg)
        }
    }

    LaunchedEffect(Unit) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (capturedBitmap != null) {
                Image(
                    bitmap = capturedBitmap!!.asImageBitmap(),
                    contentDescription = "Captured Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Close button
            TextButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Text("✕", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            // Bottom controls
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
            ) {
                if (capturedBitmap == null) {
                    // Gallery Shortcut (Bottom Left)
                    IconButton(
                        onClick = { pickImage.launch("image/*") },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 32.dp)
                            .size(64.dp)
                            .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Text("🖼️", fontSize = 28.sp)
                    }

                    // Shutter Button (Center)
                    IconButton(
                        onClick = {
                            val executor = ContextCompat.getMainExecutor(context)
                            imageCapture.takePicture(
                                executor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                                        val rotationDegrees = image.imageInfo.rotationDegrees
                                        val buffer = image.planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        image.close()

                                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        val rotatedBitmap = if (rotationDegrees != 0) {
                                            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                        } else {
                                            bitmap
                                        }

                                        capturedBitmap = rotatedBitmap
                                        // Upload the ROTATED pixels, not the raw capture: the raw
                                        // JPEG carries its orientation only as EXIF metadata, which
                                        // the decoders downstream (gallery, threads, partner's
                                        // popup) ignore — so portrait shots would show landscape.
                                        capturedBytes = ByteArrayOutputStream().let { stream ->
                                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                                            stream.toByteArray()
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        exception.printStackTrace()
                                    }
                                }
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(80.dp)
                            .background(Color.White, CircleShape)
                            .padding(4.dp)
                            .background(Color.White, CircleShape)
                    ) {
                        // Inner ring for the shutter look
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.1f))
                        )
                    }
                } else {
                    // Retry / Confirm text buttons
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Retry",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable {
                                    capturedBitmap = null
                                    capturedBytes = null
                                }
                                .padding(16.dp)
                        )

                        Text(
                            text = "OK",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    capturedBytes?.let { onCaptured(it) }
                                }
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// UploadButton was integrated into SnapButton's CameraCaptureDialog.

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
