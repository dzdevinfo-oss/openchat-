package com.openchat.app.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming

interface ApiService {
    @Streaming
    @POST("chat/completions")
    suspend fun streamChatCompletions(@Body request: ChatRequest): Response<ResponseBody>

    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: ChatRequest): Response<ResponseBody>

    @GET("models")
    suspend fun getModels(): Response<ModelListResponse>
}
