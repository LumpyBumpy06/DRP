package com.drp33.quietsignal.data.repo

interface CheckInRepository {
    suspend fun postRegisterToken(userId: Int, token: String): Result<Unit>
    suspend fun postSendOkay(userId: Int ): Result<Unit>
    suspend fun sendEmergency(userId: Int): Result<Unit>
    suspend fun getEmergencyStatus(userId: Int): Result<Boolean>
    suspend fun ackEmergency(userId: Int): Result<Unit>
    suspend fun getOkayStatus(userId: Int): Result<Boolean>
    suspend fun postVoice(clientId: Int, audio: ByteArray): Result<Unit>
    suspend fun getLatestVoice(userId: Int): Result<ByteArray>
}