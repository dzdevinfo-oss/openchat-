package com.openchat.app.data.repository

import com.openchat.app.data.model.AiModel
import com.openchat.app.data.model.ApiProvider
import com.openchat.app.data.model.Message
import com.openchat.app.data.remote.ChatMessage
import com.openchat.app.data.remote.ChatRequest
import com.openchat.app.data.remote.RetrofitBuilder
import com.openchat.app.data.remote.StreamEvent
import com.openchat.app.data.remote.StreamingParser
import com.openchat.app.data.remote.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiApiRepository @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val workspaceRepository: WorkspaceRepository
) {
    suspend fun fetchModels(provider: ApiProvider): List<AiModel> = withContext(Dispatchers.IO) {
        val apiKey = providerRepository.getApiKey(provider.id) ?: throw Exception("API key not found")
        val service = RetrofitBuilder.build(provider.baseUrl, apiKey)
        val response = service.getModels()
        if (response.isSuccessful) {
            val body = response.body()
            val list = body?.data ?: body?.models ?: emptyList()
            list.map {
                AiModel(
                    modelId = it.modelId,
                    displayName = it.modelId,
                    providerId = provider.id
                )
            }
        } else {
            throw Exception("Failed to fetch models: ${response.code()} ${response.message()}")
        }
    }

    suspend fun sendStreamingMessage(
        provider: ApiProvider,
        model: AiModel,
        chatMessages: List<ChatMessage>,
        systemPrompt: String?,
        sessionId: String,
        onToken: (String) -> Unit,
        onThinking: (String) -> Unit,
        onComplete: (String, String?) -> Unit,
        onError: (Throwable) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val apiKey = providerRepository.getApiKey(provider.id) ?: throw Exception("API key not found")
            val service = RetrofitBuilder.build(provider.baseUrl, apiKey)

            val request = ChatRequest(
                model = model.modelId,
                messages = chatMessages,
                stream = true,
                system = systemPrompt?.takeIf { it.isNotBlank() },
                thinking = if (model.displayName.contains("thinking", true) || model.modelId.contains("thinking")) ThinkingConfig() else null
            )

            val response = service.streamChatCompletions(request)
            if (!response.isSuccessful) {
                if (response.code() == 401) throw Exception("Invalid API Key (401)")
                if (response.code() == 429) throw Exception("Rate limit exceeded (429)")
                throw Exception("API Error: ${response.code()} ${response.message()}")
            }

            val body = response.body() ?: throw Exception("Empty response body")
            
            var fullContent = ""
            var fullThinking = ""

            StreamingParser.parse(body).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        fullContent += event.content
                        onToken(event.content)
                    }
                    is StreamEvent.Thinking -> {
                        fullThinking += event.content
                        onThinking(event.content)
                    }
                    is StreamEvent.Done -> {}
                }
            }

            onComplete(fullContent, if (fullThinking.isNotEmpty()) fullThinking else null)

        } catch (e: Throwable) {
            onError(e)
        }
    }

    suspend fun sendMessage(
        provider: ApiProvider,
        model: AiModel,
        messages: List<ChatMessage>,
        sessionId: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = providerRepository.getApiKey(provider.id) ?: throw Exception("API key not found")
        val service = RetrofitBuilder.build(provider.baseUrl, apiKey)

        val request = ChatRequest(
            model = model.modelId,
            messages = messages,
            stream = false
        )

        val response = service.chatCompletions(request)
        if (response.isSuccessful) {
            response.body()?.choices?.firstOrNull()?.message?.content ?: throw Exception("Empty response")
        } else {
            throw Exception("API Error: ${response.code()}")
        }
    }
}
