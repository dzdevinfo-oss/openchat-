package com.openchat.app.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openchat.app.agent.AgentManager
import com.openchat.app.agent.MemoryManager
import com.openchat.app.data.model.AiModel
import com.openchat.app.data.model.ApiProvider
import com.openchat.app.data.model.Message
import com.openchat.app.data.model.Session
import com.openchat.app.data.repository.AiApiRepository
import com.openchat.app.data.repository.ChatRepository
import com.openchat.app.data.repository.ProviderRepository
import com.openchat.app.util.SettingsManager
import com.openchat.app.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val providerRepository: ProviderRepository,
    private val aiApiRepository: AiApiRepository,
    private val memoryManager: MemoryManager,
    private val agentManager: AgentManager,
    private val workspaceRepository: com.openchat.app.data.repository.WorkspaceRepository,
    private val fileProcessor: com.openchat.app.util.FileProcessor,
    private val imageProcessor: com.openchat.app.util.ImageProcessor,
    val voiceInputManager: com.openchat.app.util.VoiceInputManager,
    val artifactDetector: com.openchat.app.util.ArtifactDetector,
    private val settingsManager: SettingsManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    val isOnline = networkMonitor.isOnline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _selectedModel = MutableStateFlow<AiModel?>(null)
    val selectedModel: StateFlow<AiModel?> = _selectedModel.asStateFlow()

    private val _selectedProvider = MutableStateFlow<ApiProvider?>(null)
    val selectedProvider: StateFlow<ApiProvider?> = _selectedProvider.asStateFlow()

    val allProviders: StateFlow<List<ApiProvider>> = providerRepository.getAllProviders()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allModels: StateFlow<List<AiModel>> = providerRepository.getAllModels()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pastSessions: StateFlow<List<Session>> = chatRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var sessionMessagesJob: Job? = null
    private var streamingJob: Job? = null

    init {
        viewModelScope.launch {
            // Load default model and provider on startup
            val builtInModels = providerRepository.getBuiltInModels().firstOrNull() ?: emptyList()
            if (builtInModels.isNotEmpty()) {
                val defaultModel = builtInModels.first()
                _selectedModel.value = defaultModel
                _selectedProvider.value = providerRepository.getProviderById(defaultModel.providerId)
            }
            newSession()
            
            // Clean up interrupted streaming messages on start
            chatRepository.getAllInterruptedMessages().forEach { msg ->
                chatRepository.updateMessage(msg.copy(isStreaming = false, content = msg.content + "\n\n⚠ Response was interrupted"))
            }
        }
    }

    fun selectModel(model: AiModel) {
        viewModelScope.launch {
            _selectedModel.value = model
            _selectedProvider.value = providerRepository.getProviderById(model.providerId)
            _currentSession.value?.let { session ->
                chatRepository.updateSession(session.copy(modelId = model.modelId, providerId = model.providerId))
            }
        }
    }

    fun newSession() {
        stopStreaming()
        val cModel = _selectedModel.value
        val cProvider = _selectedProvider.value
        if (cModel != null && cProvider != null) {
            val session = Session(modelId = cModel.modelId, providerId = cProvider.id)
            viewModelScope.launch {
                chatRepository.createSession(session)
                loadSession(session.id)
            }
        }
    }

    fun loadSession(sessionId: String) {
        stopStreaming()
        viewModelScope.launch {
            val session = chatRepository.getSessionById(sessionId)
            if (session != null) {
                _currentSession.value = session
                val provider = providerRepository.getProviderById(session.providerId)
                val models = providerRepository.getModelsByProvider(session.providerId).firstOrNull() ?: emptyList()
                val model = models.find { it.modelId == session.modelId }
                if (provider != null) _selectedProvider.value = provider
                if (model != null) _selectedModel.value = model

                sessionMessagesJob?.cancel()
                sessionMessagesJob = launch {
                    chatRepository.getMessagesBySessionId(sessionId).collect { msgs ->
                        _messages.value = msgs
                    }
                }
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (_currentSession.value?.id == sessionId) {
                newSession()
            }
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            pastSessions.value.forEach { session ->
                chatRepository.deleteSession(session.id)
            }
            newSession()
        }
    }

    fun sendMessage(content: String, attachments: List<Uri>) {
        val session = _currentSession.value ?: return
        val provider = _selectedProvider.value ?: return
        val model = _selectedModel.value ?: return

        if (!isOnline.value) return

        // Create empty assistant message
        val assistantMsgId = java.util.UUID.randomUUID().toString()
        val assistantMessage = Message(
            id = assistantMsgId,
            sessionId = session.id,
            role = "assistant",
            content = "",
            isStreaming = true
        )

        viewModelScope.launch {
            voiceInputManager.stopSpeaking()

            var finalContent = content
            val savedAttachments = mutableListOf<String>()

            for (uri in attachments) {
                try {
                    val fileContent = fileProcessor.processFile(uri)
                    if (fileContent != null && fileContent.type != "jpg" && fileContent.type != "png" && fileContent.type != "jpeg") {
                        finalContent += "\n\n[File: ${fileContent.name}]\n${fileContent.content}\n[/File]\n"
                    } else if (fileContent == null || fileContent.type == "jpg" || fileContent.type == "png" || fileContent.type == "jpeg") {
                        savedAttachments.add(uri.toString())
                    }
                } catch (e: Exception) {
                    chatRepository.insertMessage(assistantMessage.copy(content = "Error reading file: ${e.message}", isStreaming = false))
                    return@launch
                }
            }

            val userMsgId = java.util.UUID.randomUUID().toString()
            val userMessage = Message(
                id = userMsgId,
                sessionId = session.id,
                role = "user",
                content = finalContent,
                attachments = org.json.JSONArray(savedAttachments).toString()
            )

            chatRepository.insertMessage(userMessage)
            chatRepository.insertMessage(assistantMessage)
            
            // Title generation
            if (session.title == "New Chat") {
                launch {
                    try {
                        val titlePrompt = "Give this conversation a short 3-5 word title based on: $finalContent. Just the title."
                        val title = aiApiRepository.sendMessage(provider, model, listOf(com.openchat.app.data.remote.ChatMessage("user", titlePrompt)), session.id)
                        chatRepository.updateSession(session.copy(title = title.trim().removeSurrounding("\"")))
                    } catch (e: Exception) {
                        chatRepository.updateSession(session.copy(title = finalContent.take(30) + "..."))
                    }
                }
            }

            _isStreaming.value = true

            streamingJob = launch {
                val contextMessages = chatRepository.getLastNMessages(session.id, 20)
                    .filter { it.id != assistantMsgId && !it.isStreaming }
                    .sortedBy { it.timestamp }

                var baseSystemPrompt = session.systemPrompt ?: ""
                val defaultPrompt = settingsManager.defaultSystemPrompt.first()
                if (baseSystemPrompt.isBlank() && defaultPrompt.isNotBlank()) baseSystemPrompt = defaultPrompt

                if (settingsManager.autoInjectContext.first()) {
                    val workspaceFiles = workspaceRepository.getFilesBySessionId(session.id).first().take(settingsManager.maxContextFiles.first())
                    if (workspaceFiles.isNotEmpty()) {
                        baseSystemPrompt += "\n\n# Workspace Context\n" + workspaceFiles.joinToString("\n") { ">>> ${it.fileName}\n${it.content}\n<<<" }
                        baseSystemPrompt += "\nCRITICAL: Use ```action:create_file\n{\"name\":\"...\",\"content\":\"...\"}``` to manage files."
                    }
                }

                val injectedSystemPrompt = memoryManager.injectMemories(baseSystemPrompt)
                val builtMessages = com.openchat.app.util.MultimodalMessageBuilder.build(contextMessages, injectedSystemPrompt, imageProcessor, model)

                try {
                    aiApiRepository.sendStreamingMessage(
                        provider = provider,
                        model = model,
                        chatMessages = builtMessages,
                        systemPrompt = null,
                        sessionId = session.id,
                        onToken = { chunk -> launch { chatRepository.updateStreamingMessage(assistantMsgId, session.id, chunk, isComplete = false) } },
                        onThinking = { tChunk -> launch { chatRepository.updateStreamingMessage(assistantMsgId, session.id, "", thinkingChunk = tChunk, isComplete = false) } },
                        onComplete = { fullContent, fullThinking ->
                            launch {
                                handleAiWorkspaceCommands(fullContent, session.id)
                                chatRepository.updateMessage(assistantMessage.copy(content = fullContent, thinkingContent = fullThinking, isStreaming = false))
                                _isStreaming.value = false
                                memoryManager.extractMemoriesAsync(contextMessages + assistantMessage.copy(content = fullContent), provider, model)
                                if (voiceInputManager.enableTts.first()) {
                                    voiceInputManager.speak(fullContent.replace(Regex("```[\\s\\S]*?```"), "Code block skipped"), voiceInputManager.ttsSpeed.first(), voiceInputManager.ttsPitch.first())
                                }
                            }
                        },
                        onError = { error ->
                            launch {
                                chatRepository.updateMessage(assistantMessage.copy(content = "Error: ${error.message}", isStreaming = false))
                                _isStreaming.value = false
                            }
                        }
                    )
                } catch (e: Exception) {
                    chatRepository.updateMessage(assistantMessage.copy(content = "Error: ${e.message}", isStreaming = false))
                    _isStreaming.value = false
                }
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        _isStreaming.value = false
        viewModelScope.launch {
            val session = _currentSession.value ?: return@launch
            chatRepository.getMessagesBySessionId(session.id).first().find { it.isStreaming }?.let {
                chatRepository.updateMessage(it.copy(isStreaming = false))
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch { chatRepository.deleteMessageById(messageId) }
    }

    fun editAndResend(messageId: String, newContent: String) {
        viewModelScope.launch {
            val session = _currentSession.value ?: return@launch
            val msgs = chatRepository.getMessagesBySessionId(session.id).first()
            val targetMsg = msgs.find { it.id == messageId } ?: return@launch
            msgs.filter { it.timestamp >= targetMsg.timestamp }.forEach { chatRepository.deleteMessageById(it.id) }
            sendMessage(newContent, emptyList())
        }
    }

    fun regenerateLastResponse() {
        viewModelScope.launch {
            val session = _currentSession.value ?: return@launch
            val msgs = chatRepository.getLastNMessages(session.id, 2)
            val lastUserMsg = msgs.find { it.role == "user" } ?: return@launch
            msgs.find { it.role == "assistant" && it.timestamp >= lastUserMsg.timestamp }?.let { chatRepository.deleteMessageById(it.id) }
            sendMessage(lastUserMsg.content, emptyList())
        }
    }

    private suspend fun handleAiWorkspaceCommands(content: String, sessionId: String) {
        val existingFiles = workspaceRepository.getFilesBySessionId(sessionId).first()
        val createRegex = Regex("```create:([^\\n]+)\\n([\\s\\S]*?)```")
        createRegex.findAll(content).forEach { match ->
            val filename = match.groupValues[1].trim()
            if (existingFiles.none { it.fileName == filename }) {
                workspaceRepository.createFile(
                    com.openchat.app.data.model.WorkspaceFile(
                        workspaceId = sessionId, // Using sessionId as workspaceId
                        sessionId = sessionId,
                        fileName = filename,
                        filePath = "",
                        fileType = filename.substringAfterLast('.', "txt"),
                        content = match.groupValues[2]
                    )
                )
            }
        }
        val editRegex = Regex("```edit:([^\\n]+)\\n([\\s\\S]*?)```")
        editRegex.findAll(content).forEach { match ->
            val filename = match.groupValues[1].trim()
            existingFiles.find { it.fileName == filename }?.let { workspaceRepository.updateFileContent(it.id, match.groupValues[2]) }
        }
        val deleteRegex = Regex("```delete:([^\\n]+)```")
        deleteRegex.findAll(content).forEach { match ->
            val filename = match.groupValues[1].trim()
            existingFiles.find { it.fileName == filename }?.let { workspaceRepository.softDeleteFile(it.id) }
        }
    }
    
    val agentSessions: StateFlow<List<String>> = agentManager.agentSessions
    fun launchAgentTask(task: String) {
        val session = _currentSession.value ?: return
        val provider = _selectedProvider.value ?: return
        val model = _selectedModel.value ?: return
        agentManager.launchAgent(session.id, task, provider, model)
    }
    fun pauseAgent(sessionId: String) = agentManager.pauseAgent(sessionId)
    fun cancelAgent(sessionId: String) = agentManager.cancelAgent(sessionId)
    fun saveToWorkspace(fileName: String, content: String) {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            workspaceRepository.createFile(
                com.openchat.app.data.model.WorkspaceFile(
                    workspaceId = session.id,
                    sessionId = session.id,
                    fileName = fileName,
                    filePath = "",
                    fileType = fileName.substringAfterLast('.', "txt"),
                    content = content
                )
            )
        }
    }
}
