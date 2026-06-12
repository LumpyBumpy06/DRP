package com.drp33.quietsignal.data.remote

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object RetroFitProvider {
    private const val BASE_URL = "https://quietsignal.noxbound.com/"

    private var okHttpClient: OkHttpClient? = null

    fun getClient(context: Context): OkHttpClient {
        return okHttpClient ?: synchronized(this) {
            val cacheSize = 50 * 1024 * 1024L // 50MB
            val cache = Cache(File(context.cacheDir, "http_cache"), cacheSize)

            val interceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }

            OkHttpClient.Builder()
                .cache(cache)
                .addInterceptor(interceptor)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build().also { okHttpClient = it }
        }
    }

    fun getCheckInAPI(context: Context): CheckInAPI {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(getClient(context))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CheckInAPI::class.java)
    }

    // Kept for backward compatibility if context-less access is needed, 
    // but preferred usage is via getCheckInAPI(context).
    val checkInAPI: CheckInAPI by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS })
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CheckInAPI::class.java)
    }
}
