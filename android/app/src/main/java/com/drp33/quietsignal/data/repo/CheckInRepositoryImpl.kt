package com.drp33.quietsignal.data.repo

import android.util.Log
import com.drp33.quietsignal.data.remote.CheckInAPI
import com.drp33.quietsignal.data.remote.models.OkayRequest
import com.drp33.quietsignal.data.remote.models.TokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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

    override suspend fun sendEmergency(userId: Int): Result<Unit> = runCatching {
        api.postEmergency(userId)
    }

    override suspend fun getEmergencyStatus(userId: Int): Result<Boolean> = runCatching {
        api.getActiveEmergency(userId).active
    }

    override suspend fun ackEmergency(userId: Int): Result<Unit> = runCatching {
        api.postAckEmergency(userId)
    }

    override suspend fun getOkayStatus(userId: Int): Result<Boolean> = runCatching {
        val res = api.getOkayStatus(userId).okay
        Log.i("JAYCE", "Returned result is $res")
        res
    }

    override suspend fun postVoice(clientId: Int, audio: ByteArray): Result<Unit> = runCatching {
        val requestBody = audio.toRequestBody("audio/mp4".toMediaType())
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = "$clientId/${System.currentTimeMillis()}.m4a",
            body = requestBody
        )
        api.postVoice(part)
    }

    override suspend fun getLatestVoice(userId: Int): Result<ByteArray> = runCatching {
        // Read the streamed body off the main thread.
        withContext(Dispatchers.IO) {
            api.getLatestVoice(userId).bytes()
        }
    }

    override suspend fun postPhoto(clientId: Int, jpeg: ByteArray): Result<Unit> = runCatching {
        val body = jpeg.toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = "photos/$clientId/${System.currentTimeMillis()}.jpg",
            body = body,
        )
        api.postPhoto(part)
    }

    override suspend fun getLatestPhoto(userId: Int): Result<ByteArray> = runCatching {
        withContext(Dispatchers.IO) {
            api.getLatestPhoto(userId).bytes()
        }
    }

    override suspend fun getTree(): Result<com.drp33.quietsignal.model.TreeState> = runCatching {
        val r = api.getTree()
        com.drp33.quietsignal.model.TreeState(stage = r.stage, deathLevel = r.deathLevel)
    }

}