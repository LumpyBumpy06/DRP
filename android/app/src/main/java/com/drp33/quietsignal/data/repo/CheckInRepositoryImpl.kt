package com.drp33.quietsignal.data.repo

import android.util.Log
import com.drp33.quietsignal.data.remote.CheckInAPI
import com.drp33.quietsignal.data.remote.models.MemoryTagsRequest
import com.drp33.quietsignal.data.remote.models.OkayRequest
import com.drp33.quietsignal.data.remote.models.ThreadMessageDto
import com.drp33.quietsignal.data.remote.models.ThreadTextRequest
import com.drp33.quietsignal.data.remote.models.TokenRequest
import com.drp33.quietsignal.model.PromptMemory
import com.drp33.quietsignal.model.ThreadMessage
import com.drp33.quietsignal.model.ThreadSummary
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

    override suspend fun postRevive(userId: Int): Result<Unit> = runCatching {
        api.postRevive(userId)
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

    override suspend fun getMemories(): Result<List<com.drp33.quietsignal.model.MemoryItem>> = runCatching {
        api.getMemories().memories.map {
            com.drp33.quietsignal.model.MemoryItem(
                objectName = it.objectName,
                type = it.type,
                sender = it.sender,
                epoch = it.epoch,
                tags = it.tags,
            )
        }
    }

    override suspend fun getAllTags(): Result<List<String>> = runCatching {
        api.getTags().tags
    }

    override suspend fun setMemoryTags(objectName: String, tags: List<String>): Result<List<String>> = runCatching {
        api.setMemoryTags(objectName, MemoryTagsRequest(tags)).tags
    }

    override suspend fun addMemoryTag(objectName: String, tag: String): Result<List<String>> = runCatching {
        api.addMemoryTag(objectName, tag).tags
    }

    override suspend fun removeMemoryTag(objectName: String, tag: String): Result<List<String>> = runCatching {
        api.removeMemoryTag(objectName, tag).tags
    }

    override suspend fun getForest(): Result<List<com.drp33.quietsignal.model.ForestWeek>> = runCatching {
        api.getForest().weeks.map {
            com.drp33.quietsignal.model.ForestWeek(
                weekStart = it.weekStart,
                weekIndex = it.weekIndex,
                stage = it.stage,
                deathLevel = it.deathLevel,
            )
        }
    }

    override suspend fun reshare(userId: Int, objectName: String): Result<Unit> = runCatching {
        api.postReshare(userId, objectName)
    }

    override suspend fun getMedia(objectName: String): Result<ByteArray> = runCatching {
        withContext(Dispatchers.IO) {
            api.getMedia(objectName).bytes()
        }
    }

    // ---------- THREADS ----------

    private fun ThreadMessageDto.toModel() = ThreadMessage(
        id = id,
        anchor = anchor,
        senderId = senderId,
        sender = sender,
        kind = kind,
        text = text,
        mediaObject = mediaObject,
        epoch = epoch,
    )

    override suspend fun getThreads(userId: Int): Result<List<ThreadSummary>> = runCatching {
        api.getThreads(userId).threads.map {
            ThreadSummary(
                anchor = it.anchor,
                memoryType = it.memoryType,
                memorySender = it.memorySender,
                memoryObject = it.memoryObject.ifBlank { it.anchor },
                isPrompt = it.isPrompt,
                caption = it.caption,
                count = it.count,
                incoming = it.incoming,
                lastKind = it.lastKind,
                lastText = it.lastText,
                lastSenderId = it.lastSenderId,
                lastSender = it.lastSender,
                lastEpoch = it.lastEpoch,
            )
        }
    }

    override suspend fun setThreadCaption(anchor: String, caption: String, userId: Int): Result<Unit> = runCatching {
        api.postThreadCaption(anchor, caption, userId)
    }

    override suspend fun getThread(anchor: String): Result<List<ThreadMessage>> = runCatching {
        api.getThread(anchor).messages.map { it.toModel() }
    }

    override suspend fun postThreadText(anchor: String, userId: Int, text: String): Result<ThreadMessage> = runCatching {
        api.postThreadText(ThreadTextRequest(anchor = anchor, user_id = userId, text = text)).toModel()
    }

    override suspend fun postThreadVoice(anchor: String, userId: Int, audio: ByteArray): Result<ThreadMessage> = runCatching {
        val body = audio.toRequestBody("audio/mp4".toMediaType())
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = "${System.currentTimeMillis()}.m4a",
            body = body,
        )
        api.postThreadVoice(anchor, userId, part).toModel()
    }

    override suspend fun postThreadPhoto(anchor: String, userId: Int, jpeg: ByteArray): Result<ThreadMessage> = runCatching {
        val body = jpeg.toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = "${System.currentTimeMillis()}.jpg",
            body = body,
        )
        api.postThreadPhoto(anchor, userId, part).toModel()
    }

    // ---------- PROMPT ----------

    override suspend fun getPrompt(): Result<PromptMemory?> = runCatching {
        api.getPrompt().prompt?.let {
            PromptMemory(
                objectName = it.objectName,
                type = it.type,
                sender = it.sender,
                epoch = it.epoch,
                threadAnchor = it.threadAnchor.ifBlank { it.objectName },
            )
        }
    }

    override suspend fun announcePrompt(promptKey: String): Result<Unit> = runCatching {
        api.postPromptAnnounce(promptKey)
    }
}
