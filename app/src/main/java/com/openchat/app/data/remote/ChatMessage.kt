package com.openchat.app.data.remote

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    val role: String,
    val content: Any // Can be String or List<ContentBlock>
)

sealed class ContentBlock(val type: String) {
    data class TextBlock(val text: String) : ContentBlock("text")
    data class ImageBlock(
        @SerializedName("image_url") val imageUrl: ImageUrl
    ) : ContentBlock("image_url")
}

data class ImageUrl(val url: String)
