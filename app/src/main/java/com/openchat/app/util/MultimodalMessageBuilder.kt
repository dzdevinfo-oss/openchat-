package com.openchat.app.util

import com.openchat.app.data.model.AiModel
import com.openchat.app.data.model.Message
import com.openchat.app.data.remote.ChatMessage
import com.openchat.app.data.remote.ContentBlock
import com.openchat.app.data.remote.ImageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

object MultimodalMessageBuilder {

    suspend fun build(
        messages: List<Message>,
        systemPrompt: String,
        imageProcessor: ImageProcessor,
        model: AiModel
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        val chatMessages = mutableListOf<ChatMessage>()
        
        if (systemPrompt.isNotBlank()) {
            chatMessages.add(ChatMessage(role = "system", content = systemPrompt))
        }

        for (msg in messages) {
            val attachmentsList = try {
                val array = JSONArray(msg.attachments)
                List(array.length()) { array.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }

            // We filter out any attachments that might be images
            val imageUris = attachmentsList.filter { it.startsWith("content://") || it.startsWith("file://") || it.endsWith(".jpg") || it.endsWith(".png") } // Simplified check

            if (imageUris.isNotEmpty() && model.supportsVision == true) {
                val contentBlocks = mutableListOf<ContentBlock>()
                contentBlocks.add(ContentBlock.TextBlock(text = msg.content))
                
                for (uriString in imageUris) {
                    val uri = android.net.Uri.parse(uriString)
                    val base64 = imageProcessor.processImageToBase64(uri)
                    if (base64 != null) {
                        contentBlocks.add(
                            ContentBlock.ImageBlock(
                                imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64")
                            )
                        )
                    }
                }
                chatMessages.add(ChatMessage(role = msg.role, content = contentBlocks))
            } else {
                // If model doesn't support vision, just send text (and files injected as text will already be in msg.content)
                chatMessages.add(ChatMessage(role = msg.role, content = msg.content))
            }
        }

        chatMessages
    }
}
