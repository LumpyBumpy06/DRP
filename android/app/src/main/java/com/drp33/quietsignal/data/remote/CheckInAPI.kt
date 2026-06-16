package com.drp33.quietsignal.data.remote

import com.drp33.quietsignal.data.remote.models.EmergencyStatusResponse
import com.drp33.quietsignal.data.remote.models.ForestResponse
import com.drp33.quietsignal.data.remote.models.MemoriesResponse
import com.drp33.quietsignal.data.remote.models.MemoryTagsRequest
import com.drp33.quietsignal.data.remote.models.MemoryTagsResponse
import com.drp33.quietsignal.data.remote.models.TagsResponse
import com.drp33.quietsignal.data.remote.models.OkayRequest
import com.drp33.quietsignal.data.remote.models.OkayStatusResponse
import com.drp33.quietsignal.data.remote.models.PromptResponse
import com.drp33.quietsignal.data.remote.models.ThreadMessageDto
import com.drp33.quietsignal.data.remote.models.ThreadMessagesResponse
import com.drp33.quietsignal.data.remote.models.ThreadTextRequest
import com.drp33.quietsignal.data.remote.models.ThreadsResponse
import com.drp33.quietsignal.data.remote.models.TokenRequest
import com.drp33.quietsignal.data.remote.models.TreeResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface CheckInAPI {
    @POST("token")
    suspend fun postRegisterToken(@Body request: TokenRequest)

    @Multipart
    @POST("voice")
    suspend fun postVoice(@Part file: MultipartBody.Part)

    @GET("voice/latest")
    suspend fun getLatestVoice(@Query("user_id") userId: Int): ResponseBody

    @Multipart
    @POST("photo")
    suspend fun postPhoto(@Part file: MultipartBody.Part)

    @GET("photo/latest")
    suspend fun getLatestPhoto(@Query("user_id") userId: Int): ResponseBody

    @POST("okay")
//    suspend fun postSendOkay(@Body request: OkayRequest)
    suspend fun postSendOkay(@Query("user_id") userId: Int)

    @POST("revive")
    suspend fun postRevive(@Query("user_id") userId: Int)

    @POST("emergency")
    suspend fun postEmergency(@Query("user_id") userId: Int)

    @GET("emergency/active")
    suspend fun getActiveEmergency(@Query("user_id") userId: Int): EmergencyStatusResponse

    @POST("emergency/ack")
    suspend fun postAckEmergency(@Query("user_id") userId: Int)

    @GET("okay")
    suspend fun getOkayStatus(
        @Query("user_id") userId: Int
    ): OkayStatusResponse

    @GET("tree")
    suspend fun getTree(): TreeResponse

    @GET("memories")
    suspend fun getMemories(): MemoriesResponse

    @GET("forest")
    suspend fun getForest(): ForestResponse

    @GET("media")
    suspend fun getMedia(@Query("object_name") objectName: String): ResponseBody

    // Re-deliver an existing memory to the partner (notification + popup only —
    // the server does NOT add it to the gallery again).
    @POST("reshare")
    suspend fun postReshare(
        @Query("user_id") userId: Int,
        @Query("object_name") objectName: String,
    )

    // ---------- TAGS (shared labels on a memory) ----------

    @GET("tags")
    suspend fun getTags(): TagsResponse

    @POST("memory/tags")
    suspend fun setMemoryTags(
        @Query("object_name") objectName: String,
        @Body request: MemoryTagsRequest,
    ): MemoryTagsResponse

    // Atomic single-tag operations — these can never clobber other tags the way
    // a stale replace-all can, so tags survive refreshes and concurrent edits.
    @POST("memory/tags/add")
    suspend fun addMemoryTag(
        @Query("object_name") objectName: String,
        @Query("tag") tag: String,
    ): MemoryTagsResponse

    @POST("memory/tags/remove")
    suspend fun removeMemoryTag(
        @Query("object_name") objectName: String,
        @Query("tag") tag: String,
    ): MemoryTagsResponse

    // ---------- THREADS (conversations anchored to a memory) ----------

    @GET("threads")
    suspend fun getThreads(@Query("user_id") userId: Int): ThreadsResponse

    // Persist a conversation's user-given title (survives app restarts).
    // `userId` lets the server water the tree when a prompt thread is started
    // (0 = unknown/none, e.g. titling an ordinary gallery thread).
    @POST("thread/caption")
    suspend fun postThreadCaption(
        @Query("anchor") anchor: String,
        @Query("caption") caption: String,
        @Query("user_id") userId: Int = 0,
    )

    @GET("thread")
    suspend fun getThread(@Query("anchor") anchor: String): ThreadMessagesResponse

    @POST("thread/text")
    suspend fun postThreadText(@Body request: ThreadTextRequest): ThreadMessageDto

    @Multipart
    @POST("thread/voice")
    suspend fun postThreadVoice(
        @Query("anchor") anchor: String,
        @Query("user_id") userId: Int,
        @Part file: MultipartBody.Part,
    ): ThreadMessageDto

    @Multipart
    @POST("thread/photo")
    suspend fun postThreadPhoto(
        @Query("anchor") anchor: String,
        @Query("user_id") userId: Int,
        @Part file: MultipartBody.Part,
    ): ThreadMessageDto

    // ---------- PROMPT (a memory the tree resurfaces when quiet) ----------

    @GET("prompt")
    suspend fun getPrompt(): PromptResponse
}
