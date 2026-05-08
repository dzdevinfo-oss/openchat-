package com.openchat.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.openchat.app.ui.theme.PrimaryTeal
import com.openchat.app.util.VoiceInputManager

@Composable
fun InputBar(
    isStreaming: Boolean,
    onSendMessage: (String, List<Uri>) -> Unit,
    onStopStreaming: () -> Unit,
    voiceInputManager: VoiceInputManager,
    supportsVision: Boolean,
    enabled: Boolean = true
) {
    var text by remember { mutableStateOf("") }
    val attachedUris = remember { mutableStateListOf<Uri>() }
    val context = LocalContext.current

    val isListening by voiceInputManager.isListeningState.collectAsState()
    val partialTranscript by voiceInputManager.partialTranscript.collectAsState()
    val finalTranscript by voiceInputManager.finalTranscript.collectAsState()

    LaunchedEffect(partialTranscript) {
        if (partialTranscript.isNotBlank() && enabled) text = partialTranscript
    }
    LaunchedEffect(finalTranscript) {
        if (finalTranscript.isNotBlank() && enabled) {
            text = finalTranscript
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!supportsVision && uris.isNotEmpty()) {
            android.widget.Toast.makeText(context, "Current model does not support Vision. Images will be ignored.", android.widget.Toast.LENGTH_LONG).show()
        }
        attachedUris.addAll(uris.take(5 - attachedUris.size)) // Max 5 images
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { attachedUris.add(it) }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column {
            androidx.compose.animation.AnimatedVisibility(
                visible = attachedUris.isNotEmpty(),
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachedUris) { uri ->
                        Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Attachment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            IconButton(
                                onClick = { attachedUris.remove(uri) },
                                modifier = Modifier.align(Alignment.TopEnd).size(20.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = { if (enabled) filePicker.launch("*/*") }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach File", tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
                IconButton(onClick = { if (enabled && attachedUris.size < 5) imagePicker.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = "Attach Image", tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
                
                Box(modifier = Modifier.weight(1f).padding(bottom = 12.dp, top = 12.dp)) {
                    if (text.isEmpty() && !isListening) {
                        Text("Message...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    } else if (text.isEmpty() && isListening) {
                        Text("Listening...", color = PrimaryTeal.copy(alpha = 0.8f))
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { if (enabled) text = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                        cursorBrush = SolidColor(PrimaryTeal),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5,
                        enabled = enabled
                    )
                }
                
                if (isStreaming) {
                    IconButton(onClick = onStopStreaming) {
                        Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.error))
                    }
                } else if (text.isNotBlank() || attachedUris.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            if (isListening) voiceInputManager.stopListening()
                            onSendMessage(text, attachedUris.toList())
                            text = ""
                            attachedUris.clear()
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = PrimaryTeal, contentColor = Color.White),
                        modifier = Modifier.clip(CircleShape),
                        enabled = enabled
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                    }
                } else {
                    val infiniteTransition = rememberInfiniteTransition()
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isListening) 1.2f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    IconButton(
                        onClick = {
                            if (enabled) {
                                if (isListening) voiceInputManager.stopListening()
                                else voiceInputManager.startListening()
                            }
                        },
                        modifier = Modifier.scale(scale)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice",
                            tint = if (enabled && isListening) PrimaryTeal else MaterialTheme.colorScheme.onSurfaceVariant.let { if (enabled) it else it.copy(alpha = 0.3f) }
                        )
                    }
                }
            }
        }
    }
}
