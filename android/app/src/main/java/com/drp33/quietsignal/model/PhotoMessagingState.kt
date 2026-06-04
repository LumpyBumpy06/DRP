package com.drp33.quietsignal.model

import androidx.compose.ui.graphics.ImageBitmap

/** Latest received snap (decoded for display) + a transient status line. */
data class PhotoMessagingState(
    val image: ImageBitmap? = null,
    val status: String = "",
)
