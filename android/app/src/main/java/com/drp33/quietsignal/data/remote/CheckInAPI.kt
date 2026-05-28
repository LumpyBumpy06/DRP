package com.drp33.quietsignal.data.remote

import com.drp33.quietsignal.data.remote.models.OkayRequest
import com.drp33.quietsignal.data.remote.models.OkayStatusResponse
import com.drp33.quietsignal.data.remote.models.TokenRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface CheckInAPI {
    @POST("token")
    suspend fun postRegisterToken(@Body request: TokenRequest)

    @POST("okay")
    suspend fun postSendOkay(@Body request: OkayRequest)

    @GET("okay")
    suspend fun getOkayStatus(): OkayStatusResponse
}