package com.drp33.quietsignal.data.repo

import com.drp33.quietsignal.model.TreeState

interface CheckInRepository {
    suspend fun postRegisterToken(userId: Int, token: String): Result<Unit>
    suspend fun postSendOkay(userId: Int ): Result<Unit>
    suspend fun getOkayStatus(userId: Int): Result<Boolean>
    suspend fun postVoice(clientId: Int, audio: ByteArray): Result<Unit>
    suspend fun getLatestVoice(userId: Int): Result<ByteArray>
    suspend fun getTree(): Result<TreeState>
}