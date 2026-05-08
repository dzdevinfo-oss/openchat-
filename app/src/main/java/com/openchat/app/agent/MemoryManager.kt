package com.openchat.app.agent

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.openchat.app.data.db.MemoryDao
import com.openchat.app.data.model.AiModel
import com.openchat.app.data.model.ApiProvider
import com.openchat.app.data.model.Memory
import com.openchat.app.data.model.Message
import com.openchat.app.data.remote.ChatMessage
import com.openchat.app.data.remote.ChatRequest
import com.openchat.app.data.remote.RetrofitBuilder
import com.openchat.app.data.repository.ProviderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "memory_settings")

@Singleton
class MemoryManager @Inject constructor(
    private val memoryDao: MemoryDao,
    private val providerRepository: ProviderRepository,
    @ApplicationContext private val context: Context
) {
    private val isMemoryEnabledKey = booleanPreferencesKey("is_memory_enabled")

    val isMemoryEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[isMemoryEnabledKey] ?: true // Default enabled
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[isMemoryEnabledKey] = enabled
        }
    }

    fun getAllMemories(): Flow<List<Memory>> = memoryDao.getAll()

    suspend fun clearAllMemories() {
        memoryDao.deleteAllMemories()
    }
    
    suspend fun deleteMemory(id: String) {
        memoryDao.delete(id)
    }

    suspend fun injectMemories(systemPrompt: String): String {
        return withContext(Dispatchers.IO) {
            val enabled = isMemoryEnabled.first()
            if (!enabled) return@withContext systemPrompt
            
            val memories = memoryDao.getGlobal().first()
            if (memories.isEmpty()) return@withContext systemPrompt
            
            val memoryText = memories.joinToString("\n") { "- ${it.content}" }
            val prefix = "[MEMORIES]\n$memoryText\n[/MEMORIES]\n\n"
            
            prefix + systemPrompt
        }
    }

    fun extractMemoriesAsync(
        messages: List<Message>, 
        provider: ApiProvider, 
        model: AiModel
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = isMemoryEnabled.first()
                if (!enabled) return@launch
                
                // Only take last 3 messages
                val lastMessages = messages.takeLast(3)
                if (lastMessages.isEmpty()) return@launch

                val systemCommand = """
                    Extract any facts worth remembering from this conversation. Focus on user preferences, names, facts, and persistent context.
                    Return result strictly as a JSON array: [{"fact": "string", "important": boolean}] or empty array [].
                    Do not return markdown, just the JSON string array. Wait, if it's not important do not extract it.
                """.trimIndent()
                
                val chatMessages = mutableListOf<ChatMessage>()
                chatMessages.add(ChatMessage(role = "system", content = systemCommand))
                chatMessages.addAll(lastMessages.map { ChatMessage(role = it.role, content = it.content) })

                val apiKey = providerRepository.getApiKey(provider.id) ?: return@launch
                val service = RetrofitBuilder.build(provider.baseUrl, apiKey)

                val request = ChatRequest(
                    model = model.modelId,
                    messages = chatMessages,
                    stream = false
                )

                val response = service.chatCompletions(request)
                if (response.isSuccessful) {
                    val responseStr = response.body()?.string() ?: return@launch
                    val jsonResponse = JSONObject(responseStr)
                    val choices = jsonResponse.optJSONArray("choices") ?: return@launch
                    if (choices.length() > 0) {
                        val messageObj = choices.getJSONObject(0).optJSONObject("message")
                        var content = messageObj?.optString("content") ?: ""
                        
                        // Clean up possible markdown wrapper
                        content = content.trim()
                        if (content.startsWith("```json")) {
                            content = content.removePrefix("```json")
                            content = content.removeSuffix("```")
                        } else if (content.startsWith("```")) {
                            content = content.removePrefix("```")
                            content = content.removeSuffix("```")
                        }
                        content = content.trim()

                        try {
                            val jsonArray = JSONArray(content)
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val fact = obj.optString("fact")
                                val important = obj.optBoolean("important", false)
                                
                                if (fact.isNotBlank()) {
                                    val memory = Memory(
                                        content = fact,
                                        isActive = true
                                    )
                                    memoryDao.insert(memory)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
