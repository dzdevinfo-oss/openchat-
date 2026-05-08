package com.openchat.app.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.openchat.app.data.model.Message
import com.openchat.app.ui.theme.PrimaryTeal
import org.json.JSONArray
import com.openchat.app.util.MessageChunker
import com.openchat.app.util.MessageChunk

@Composable
fun ChatList(
    messages: List<Message>,
    listState: LazyListState = rememberLazyListState(),
    onDelete: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onRegenerate: () -> Unit,
    onSaveToWorkspace: (String, String) -> Unit = { _, _ -> },
    messageBubbleStyle: String = "Modern",
    showTimestamps: Boolean = true
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            MessageRow(message, onSaveToWorkspace, messageBubbleStyle, showTimestamps)
        }
        
        val lastMessage = messages.lastOrNull()
        if (lastMessage != null && lastMessage.isStreaming && lastMessage.content.isEmpty()) {
            item {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    TypingIndicator()
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val scales = listOf(
        infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(animation = androidx.compose.animation.core.tween(600), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse)
        ),
        infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(animation = androidx.compose.animation.core.tween(600, delayMillis = 200), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse)
        ),
        infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(animation = androidx.compose.animation.core.tween(600, delayMillis = 400), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse)
        )
    )

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        scales.forEach { scale ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale.value)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            )
        }
    }
}

@Composable
fun MessageRow(
    message: Message, 
    onSaveToWorkspace: (String, String) -> Unit,
    messageBubbleStyle: String = "Modern",
    showTimestamps: Boolean = true
) {
    val isUser = message.role == "user"
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)) + slideInVertically(initialOffsetY = { 20 }, animationSpec = tween(150))
    ) {
        val cursorVisible = remember { mutableStateOf(true) }
        if (message.isStreaming && !isUser) {
            LaunchedEffect(Unit) {
                while (true) {
                    cursorVisible.value = !cursorVisible.value
                    kotlinx.coroutines.delay(500)
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                if (!isUser) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                
                Column(Modifier.weight(1f, fill = false)) {
                    if (!message.thinkingContent.isNullOrBlank()) {
                        ThinkingBlock(message.thinkingContent)
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    val bubbleShape = when (messageBubbleStyle) {
                        "Minimal" -> RoundedCornerShape(8.dp)
                        "Classic" -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = if (isUser) 24.dp else 2.dp, bottomEnd = if (isUser) 2.dp else 24.dp)
                        else -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 16.dp)
                    }

                    Box(Modifier.clip(bubbleShape).background(if (isUser) PrimaryTeal else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (isUser) {
                            val uris = try { JSONArray(message.attachments).let { array -> List(array.length()) { array.getString(it) } } } catch (e: Exception) { emptyList() }
                            Column {
                                if (uris.isNotEmpty()) {
                                    LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(uris) { uriString ->
                                            AsyncImage(model = Uri.parse(uriString), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                                        }
                                    }
                                }
                                SelectionContainer { Text(text = message.content, color = Color.White, style = MaterialTheme.typography.bodyLarge) }
                            }
                        } else {
                            val fullContent = if (message.isStreaming) (if (cursorVisible.value) message.content + " ┃" else message.content + "  ") else message.content
                            val chunks = remember(fullContent) { MessageChunker.chunk(fullContent) }
                            Column(Modifier.fillMaxWidth()) {
                                chunks.forEach { chunk ->
                                    when (chunk) {
                                        is MessageChunk.Text -> if (chunk.content.isNotBlank()) MarkdownText(markdown = chunk.content, modifier = Modifier.fillMaxWidth())
                                        is MessageChunk.Code -> CodeBlockView(language = chunk.language, code = chunk.content, onSaveToWorkspace = onSaveToWorkspace)
                                    }
                                }
                            }
                        }
                    }
                    if (showTimestamps) {
                        Spacer(Modifier.height(4.dp))
                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingBlock(thinkingContent: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(if (expanded) "Thinking..." else "Thought process", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Text(text = thinkingContent, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp))
        }
    }
}
