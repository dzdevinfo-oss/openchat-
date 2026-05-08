package com.openchat.app.data.remote

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val max_tokens: Int? = null,
    val temperature: Double? = null,
    val system: String? = null,
    val thinking: ThinkingConfig? = null
)

data class ThinkingConfig(
    val type: String = "enabled",
    val budget_tokens: Int = 10000
)
