package com.openchat.app.util

import com.openchat.app.data.db.AiModelDao
import com.openchat.app.data.db.ApiProviderDao
import com.openchat.app.data.model.AiModel
import com.openchat.app.data.model.ApiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val apiProviderDao: ApiProviderDao,
    private val aiModelDao: AiModelDao
) {
    suspend fun seedDatabase() = withContext(Dispatchers.IO) {
        val count = apiProviderDao.getProviderCount()
        if (count == 0) {
            val google = ApiProvider(name = "Google AI Studio", baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/", encryptedApiKey = "", isActive = true)
            val openRouter = ApiProvider(name = "OpenRouter", baseUrl = "https://openrouter.ai/api/v1", encryptedApiKey = "")
            val openai = ApiProvider(name = "OpenAI", baseUrl = "https://api.openai.com/v1", encryptedApiKey = "")
            val groq = ApiProvider(name = "Groq", baseUrl = "https://api.groq.com/openai/v1", encryptedApiKey = "")
            val together = ApiProvider(name = "Together AI", baseUrl = "https://api.together.xyz/v1", encryptedApiKey = "")
            val mistral = ApiProvider(name = "Mistral", baseUrl = "https://api.mistral.ai/v1", encryptedApiKey = "")

            apiProviderDao.insert(google)
            apiProviderDao.insert(openRouter)
            apiProviderDao.insert(openai)
            apiProviderDao.insert(groq)
            apiProviderDao.insert(together)
            apiProviderDao.insert(mistral)

            // Google AI Studio Models
            val googleModels = listOf(
                "gemini-2.5-pro-preview-05-06",
                "gemini-2.5-flash-preview-04-17",
                "gemini-2.0-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-pro",
                "gemini-1.5-flash"
            )
            googleModels.forEach { modelId ->
                aiModelDao.insert(AiModel(modelId = modelId, displayName = modelId, providerId = google.id, isBuiltIn = true))
            }

            // OpenRouter Models
            val openRouterModels = listOf(
                "google/gemini-2.5-pro-preview",
                "anthropic/claude-sonnet-4-5",
                "anthropic/claude-opus-4",
                "openai/gpt-4o",
                "openai/o3",
                "meta-llama/llama-3.3-70b-instruct",
                "qwen/qwen3-32b",
                "mistralai/mistral-large"
            )
            openRouterModels.forEach { modelId ->
                aiModelDao.insert(AiModel(modelId = modelId, displayName = modelId.substringAfter("/"), providerId = openRouter.id, isBuiltIn = true))
            }
        }
    }
}
