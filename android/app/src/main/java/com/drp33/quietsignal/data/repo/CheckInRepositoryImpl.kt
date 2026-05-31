package com.drp33.quietsignal.data.repo

import android.util.Log
import com.drp33.quietsignal.data.remote.CheckInAPI
import com.drp33.quietsignal.data.remote.models.OkayRequest
import com.drp33.quietsignal.data.remote.models.TokenRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/* Adapter for RetroFit's CheckInApi to comply with CheckInRepository */
class CheckInRepositoryImpl(private val api: CheckInAPI): CheckInRepository {
    override suspend fun postRegisterToken(
        userId: Int,
        token: String
    ): Result<Unit> = runCatching {
        api.postRegisterToken(TokenRequest(userId, token))
    }

    override suspend fun postSendOkay(userId: Int): Result<Unit> = runCatching {
        api.postSendOkay(userId)
    }

    override suspend fun getOkayStatus(userId: Int): Result<Boolean> = runCatching {
        val res = api.getOkayStatus(userId).okay
        Log.i("JAYCE", "Returned result is $res")
        res
    }

    override suspend fun postVoice(audio: ByteArray): Result<Unit> = runCatching {
        val body = audio.toRequestBody("application/octet-stream".toMediaType())
        api.postVoice(body)
    }

}