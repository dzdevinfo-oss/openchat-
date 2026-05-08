package com.openchat.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.InputStreamReader

object StreamingParser {
    fun parse(responseBody: ResponseBody): Flow<StreamEvent> = flow {
        val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
        val gson = Gson()
        
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty()) continue
                
                if (currentLine.startsWith("data: ")) {
                    val data = currentLine.substring(6).trim()
                    if (data == "[DONE]") {
                        emit(StreamEvent.Done)
                        break
                    }
                    
                    try {
                        val json = gson.fromJson(data, JsonObject::class.java)
                        
                        // OpenAI format
                        if (json.has("choices")) {
                            val choices = json.getAsJsonArray("choices")
                            if (choices.size() > 0) {
                                val choice = choices[0].asJsonObject
                                if (choice.has("delta")) {
                                    val delta = choice.getAsJsonObject("delta")
                                    if (delta.has("content") && !delta.get("content").isJsonNull) {
                                        emit(StreamEvent.Token(delta.get("content").asString))
                                    }
                                    if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull) {
                                        emit(StreamEvent.Thinking(delta.get("reasoning_content").asString))
                                    }
                                }
                            }
                        }
                        
                        // Anthropic format
                        if (json.has("type")) {
                            when (json.get("type").asString) {
                                "content_block_delta" -> {
                                    if (json.has("delta")) {
                                        val delta = json.getAsJsonObject("delta")
                                        if (delta.has("type")) {
                                            when (delta.get("type").asString) {
                                                "text_delta" -> emit(StreamEvent.Token(delta.get("text").asString))
                                                "thinking_delta" -> emit(StreamEvent.Thinking(delta.get("thinking").asString))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } finally {
            reader.close()
        }
    }
}

sealed class StreamEvent {
    data class Token(val content: String) : StreamEvent()
    data class Thinking(val content: String) : StreamEvent()
    object Done : StreamEvent()
}
