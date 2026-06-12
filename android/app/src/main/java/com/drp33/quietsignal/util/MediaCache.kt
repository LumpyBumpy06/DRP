package com.drp33.quietsignal.util

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * A simple in-memory cache for decoded bitmaps and raw bytes. Prevents redundant
 * decodes and network requests for the same media object during the session.
 */
object MediaCache {
    // Cache up to 100 bitmaps in memory.
    private val bitmapCache = LruCache<String, ImageBitmap>(100)
    
    // Cache up to 20MB of raw media bytes (voice memos/photos) in memory.
    private val bytesCache = object : LruCache<String, ByteArray>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    fun get(key: String): ImageBitmap? = bitmapCache.get(key)
    fun put(key: String, bitmap: ImageBitmap) { bitmapCache.put(key, bitmap) }

    fun getBytes(key: String): ByteArray? = bytesCache.get(key)
    fun putBytes(key: String, bytes: ByteArray) { bytesCache.put(key, bytes) }

    fun clear() {
        bitmapCache.evictAll()
        bytesCache.evictAll()
    }
}
