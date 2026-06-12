package com.drp33.quietsignal.util

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * A simple in-memory cache for decoded thumbnails. Prevents redundant decodes
 * and network requests for the same media object during the session.
 */
object MediaCache {
    // Cache up to 100 bitmaps in memory.
    private val cache = LruCache<String, ImageBitmap>(100)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}
