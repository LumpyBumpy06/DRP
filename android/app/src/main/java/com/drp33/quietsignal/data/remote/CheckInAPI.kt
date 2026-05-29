package com.drp33.quietsignal.data.remote

import com.drp33.quietsignal.data.remote.models.OkayRequest
import com.drp33.quietsignal.data.remote.models.OkayStatusResponse
import com.drp33.quietsignal.data.remote.models.TokenRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface CheckInAPI {
    @POST("token")
    suspend fun postRegisterToken(@Body request: TokenRequest)

    @POST("okay")
//    suspend fun postSendOkay(@Body request: OkayRequest)
    suspend fun postSendOkay(@Query("user_id") userId: Int)

    @GET("okay")
    suspend fun getOkayStatus(
        @Query("user_id") userId: Int
    ): OkayStatusResponse
}