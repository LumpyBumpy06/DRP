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
    suspend fun postPhoto(clientId: Int, jpeg: ByteArray): Result<Unit>
    suspend fun getLatestPhoto(userId: Int): Result<ByteArray>
    suspend fun getTree(): Result<com.drp33.quietsignal.model.TreeState>
    suspend fun getMemories(): Result<List<com.drp33.quietsignal.model.MemoryItem>>
    suspend fun getMedia(objectName: String): Result<ByteArray>
}