package com.drp33.quietsignal.data.repo

import com.drp33.quietsignal.data.remote.CheckInAPI
import com.drp33.quietsignal.data.remote.models.OkayRequest
import com.drp33.quietsignal.data.remote.models.TokenRequest

/* Adapter for RetroFit's CheckInApi to comply with CheckInRepository */
class CheckInRepositoryImpl(private val api: CheckInAPI): CheckInRepository {
    override suspend fun postRegisterToken(
        userId: Int,
        token: String
    ): Result<Unit> = runCatching {
        api.postRegisterToken(TokenRequest(userId, token))
    }

    override suspend fun postSendOkay(userId: Int): Result<Unit> = runCatching {
        api.postSendOkay(OkayRequest(userId))
    }

    override suspend fun getOkayStatus(userId: Int): Result<Boolean> = runCatching {
        api.getOkayStatus(userId).okay
    }

}