package com.drp33.quietsignal.data.repo

import com.drp33.quietsignal.model.PromptMemory
import com.drp33.quietsignal.model.ThreadMessage
import com.drp33.quietsignal.model.ThreadSummary

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
    suspend fun getForest(): Result<List<com.drp33.quietsignal.model.ForestWeek>>
    suspend fun getMedia(objectName: String): Result<ByteArray>
    suspend fun reshare(userId: Int, objectName: String): Result<Unit>

    // ---------- TAGS (shared labels on a memory) ----------
    suspend fun getAllTags(): Result<List<String>>
    suspend fun setMemoryTags(objectName: String, tags: List<String>): Result<List<String>>

    // ---------- THREADS (conversations anchored to a memory) ----------
    suspend fun getThreads(userId: Int): Result<List<ThreadSummary>>
    suspend fun getThread(anchor: String): Result<List<ThreadMessage>>
    suspend fun postThreadText(anchor: String, userId: Int, text: String): Result<ThreadMessage>
    suspend fun postThreadVoice(anchor: String, userId: Int, audio: ByteArray): Result<ThreadMessage>
    suspend fun postThreadPhoto(anchor: String, userId: Int, jpeg: ByteArray): Result<ThreadMessage>

    // ---------- PROMPT (resurfaced memory) ----------
    suspend fun getPrompt(): Result<PromptMemory?>
}
