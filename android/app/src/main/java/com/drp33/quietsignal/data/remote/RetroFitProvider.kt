package com.drp33.quietsignal.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetroFitProvider {
//    private const val BASE_URL = "https://quietsignal.noxbound.com/"  // production
//    private const val BASE_URL = "http://10.0.2.2:8000/"              // Android emulator: alias for the host machine's loopback
    private const val BASE_URL = "http://127.0.0.1:8000"           // physical device: this computer's LAN IP (uvicorn must bind 0.0.0.0)

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(
            GsonConverterFactory.create()
        )
        .build()

    val checkInAPI: CheckInAPI = retrofit.create(CheckInAPI::class.java)
}
