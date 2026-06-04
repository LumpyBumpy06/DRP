package com.drp33.quietsignal.data.remote

import com.drp33.quietsignal.data.remote.models.OkayRequest
import com.drp33.quietsignal.data.remote.models.OkayStatusResponse
import com.drp33.quietsignal.data.remote.models.TokenRequest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface CheckInAPI {
    @POST("token")
    suspend fun postRegisterToken(@Body request: TokenRequest)

    @Multipart
    @POST("voice")
    suspend fun postVoice(@Part file: MultipartBody.Part)

    @GET("voice/latest")
    suspend fun getLatestVoice(@Query("user_id") userId: Int): ResponseBody

    @POST("okay")
//    suspend fun postSendOkay(@Body request: OkayRequest)
    suspend fun postSendOkay(@Query("user_id") userId: Int)

    @POST("emergency")
    suspend fun postEmergency(@Query("user_id") userId: Int)

    @GET("okay")
    suspend fun getOkayStatus(
        @Query("user_id") userId: Int
    ): OkayStatusResponse
}