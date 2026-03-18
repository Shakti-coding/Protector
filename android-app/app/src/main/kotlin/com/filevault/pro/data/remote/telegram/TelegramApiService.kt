package com.filevault.pro.data.remote.telegram

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface TelegramApiService {

    @Multipart
    @POST("bot{token}/sendPhoto")
    suspend fun sendPhoto(
        @Path("token") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part photo: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null
    ): Response<TelegramResponse>

    @Multipart
    @POST("bot{token}/sendVideo")
    suspend fun sendVideo(
        @Path("token") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part video: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null
    ): Response<TelegramResponse>

    @Multipart
    @POST("bot{token}/sendAudio")
    suspend fun sendAudio(
        @Path("token") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part audio: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null
    ): Response<TelegramResponse>

    @Multipart
    @POST("bot{token}/sendDocument")
    suspend fun sendDocument(
        @Path("token") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part document: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null
    ): Response<TelegramResponse>

    @POST("bot{token}/getMe")
    suspend fun getMe(@Path("token") token: String): Response<TelegramMeResponse>
}

data class TelegramResponse(
    val ok: Boolean,
    val description: String? = null
)

data class TelegramMeResponse(
    val ok: Boolean,
    val result: BotInfo? = null,
    val description: String? = null
)

data class BotInfo(
    val id: Long,
    val first_name: String,
    val username: String
)
