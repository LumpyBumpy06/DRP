package com.drp33.quietsignal.data.repo

interface CheckInRepository {
    suspend fun postRegisterToken(userId: Int, token: String): Result<Unit>
    suspend fun postSendOkay(userId: Int ): Result<Unit>
    suspend fun getOkayStatus(): Result<Boolean>
}