package com.openchat.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openchat.app.data.model.AiModel
import com.openchat.app.data.model.Message
import com.openchat.app.data.model.Session
import com.openchat.app.ui.theme.PrimaryTeal
import com.openchat.app.ui.viewmodels.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    sessionId: String = "new",
    viewModel: ChatViewModel = hiltViewModel(),
    settingsViewModel: com.openchat.app.ui.viewmodels.SettingsViewModel = hiltViewModel(),
    onOpenSettings: () -> Unit = {},
    onOpenWorkspace: (String) -> Unit = {},
    onSessionSelected: (String) -> Unit = {}
) {
    val currentSession by viewModel.currentSession.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val pastSessions by viewModel.pastSessions.collectAsState()
    val allModels by viewModel.allModels.collectAsState()
    val allProviders by viewModel.allProviders.collectAsState()
    val agentSessions by viewModel.agentSessions.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    val messageBubbleStyle by settingsViewModel.messageBubbleStyle.collectAsState()
    val showTimestamps by settingsViewModel.showTimestamps.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(sessionId) {
        if (sessionId == "new") viewModel.newSession() else viewModel.loadSession(sessionId)
    }

    var showModelPicker by remember { mutableStateOf(false) }
    var showApiConfig by remember { mutableStateOf(false) }
    var showCustomModels by remember { mutableStateOf(false) }
    var artifacts by remember { mutableStateOf<List<com.openchat.app.util.Artifact>>(emptyList()) }
    var showArtifactPanel by remember { mutableStateOf(false) }

    LaunchedEffect(messages) {
        val lastMsg = messages.lastOrNull { it.role == "assistant" && !it.isStreaming }
        if (lastMsg != null) {
            val detected = viewModel.artifactDetector.detect(lastMsg.content)
            if (detected.isNotEmpty()) {
                artifacts = detected
                showArtifactPanel = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    pastSessions = pastSessions,
                    agentSessionIds = agentSessions,
                    onSessionSelect = { 
                        onSessionSelected(it.id)
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        onSessionSelected("new")
                        scope.launch { drawerState.close() }
                    },
                    onClearAll = { viewModel.clearAllSessions() },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    onOpenApiConfig = {
                        scope.launch { drawerState.close() }
                        showApiConfig = true
                    },
                    onOpenCustomModels = {
                        scope.launch { drawerState.close() }
                        showCustomModels = true
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showModelPicker = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(20.dp), tint = PrimaryTeal)
                            Spacer(Modifier.width(8.dp))
                            Text(selectedModel?.displayName ?: "Select Model", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Default.KeyboardArrowDown, null)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    actions = {
                        if (artifacts.isNotEmpty()) {
                            IconButton(onClick = { showArtifactPanel = !showArtifactPanel }) {
                                Icon(Icons.Default.Terminal, "Artifacts")
                            }
                        }
                        IconButton(onClick = { currentSession?.id?.let { onOpenWorkspace(it) } }) {
                            Icon(Icons.Default.Code, "Workspace")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                InputBar(
                    isStreaming = isStreaming,
                    onSendMessage = { text, uris -> 
                        if (text.trim().startsWith("/agent ")) viewModel.launchAgentTask(text.trim().removePrefix("/agent ").trim())
                        else viewModel.sendMessage(text, uris)
                    },
                    onStopStreaming = { viewModel.stopStreaming() },
                    voiceInputManager = viewModel.voiceInputManager,
                    supportsVision = selectedModel?.supportsVision ?: false,
                    enabled = isOnline
                )
            }
        ) { paddingValues ->
            Box(Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)) {
                Column(Modifier.fillMaxSize()) {
                    if (!isOnline) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                            Text("No internet connection", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), textAlign = TextAlign.Center)
                        }
                    }

                    if (messages.isEmpty()) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                                Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(16.dp))
                                Text("What can I help you with?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(32.dp))
                                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, maxItemsInEachRow = 2) {
                                    listOf("Write a Python script", "Explain quantum computing", "Plan a workout", "Help me debug code").forEach { suggestion ->
                                        SuggestionChip(
                                            modifier = Modifier.padding(4.dp),
                                            onClick = { viewModel.sendMessage(suggestion, emptyList()) },
                                            label = { Text(suggestion) },
                                            enabled = isOnline
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val listState = rememberLazyListState()
                        chatRepository_rememberScrollToBottom(listState, messages.size, isStreaming)
                        
                        Box(Modifier.weight(1f)) {
                            ChatList(
                                messages = messages,
                                listState = listState,
                                onDelete = { viewModel.deleteMessage(it) },
                                onEdit = { id, text -> viewModel.editAndResend(id, text) },
                                onRegenerate = { viewModel.regenerateLastResponse() },
                                onSaveToWorkspace = { fileName, content -> viewModel.saveToWorkspace(fileName, content) },
                                messageBubbleStyle = messageBubbleStyle,
                                showTimestamps = showTimestamps
                            )
                        }
                    }
                }
            }
        }
    }

    if (showModelPicker) ModelPickerBottomSheet(allModels, allProviders, selectedModel, { viewModel.selectModel(it); showModelPicker = false }, { showModelPicker = false })
    if (showApiConfig) ApiConfigBottomSheet { showApiConfig = false }
    if (showCustomModels) CustomModelsBottomSheet { showCustomModels = false }

    if (showArtifactPanel && artifacts.isNotEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.End) {
                AnimatedVisibility(visible = showArtifactPanel, enter = slideInHorizontally(initialOffsetX = { it }), exit = slideOutHorizontally(targetOffsetX = { it })) {
                    ArtifactPanel(artifacts.first(), { showArtifactPanel = false })
                }
            }
        }
    }
}

@Composable
fun chatRepository_rememberScrollToBottom(listState: androidx.compose.foundation.lazy.LazyListState, messagesCount: Int, isStreaming: Boolean) {
    LaunchedEffect(messagesCount, isStreaming) {
        if (messagesCount > 0) {
            listState.animateScrollToItem(messagesCount)
        }
    }
}

@Composable
fun DrawerContent(
    pastSessions: List<Session>,
    agentSessionIds: List<String>,
    onSessionSelect: (Session) -> Unit,
    onNewChat: () -> Unit,
    onClearAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenApiConfig: () -> Unit,
    onOpenCustomModels: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onNewChat, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("New Chat")
            }
        }
        OutlinedTextField(value = "", onValueChange = {}, placeholder = { Text("Search chats...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = PrimaryTeal, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("RECENT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClearAll) { Text("Clear All", color = MaterialTheme.colorScheme.error) }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(pastSessions) { session ->
                val date = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.updatedAt))
                Row(Modifier.fillMaxWidth().clickable { onSessionSelect(session) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (agentSessionIds.contains(session.id)) Icon(Icons.Default.SmartToy, "Agent", tint = PrimaryTeal, modifier = Modifier.padding(end = 8.dp).size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(session.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(16.dp)) {
            DrawerActionItem(Icons.Default.Settings, "Settings", onOpenSettings)
            DrawerActionItem(Icons.Default.VpnKey, "API Config", onOpenApiConfig)
            DrawerActionItem(Icons.Default.ModelTraining, "Custom Models", onOpenCustomModels)
        }
    }
}

@Composable
fun DrawerActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}
