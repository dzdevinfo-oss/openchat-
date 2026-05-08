package com.openchat.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openchat.app.data.model.AiModel
import com.openchat.app.data.model.ApiProvider
import com.openchat.app.data.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val memoryManager: com.openchat.app.agent.MemoryManager,
    private val voiceInputManager: com.openchat.app.util.VoiceInputManager,
    private val settingsManager: com.openchat.app.util.SettingsManager
) : ViewModel() {

    // Appearance
    val darkTheme = settingsManager.darkTheme.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val fontSize = settingsManager.fontSize.stateIn(viewModelScope, SharingStarted.Eagerly, 16f)
    val messageBubbleStyle = settingsManager.messageBubbleStyle.stateIn(viewModelScope, SharingStarted.Eagerly, "Modern")
    val showTimestamps = settingsManager.showTimestamps.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch { settingsManager.setDarkTheme(enabled) }
    fun setFontSize(size: Float) = viewModelScope.launch { settingsManager.setFontSize(size) }
    fun setMessageBubbleStyle(style: String) = viewModelScope.launch { settingsManager.setMessageBubbleStyle(style) }
    fun setShowTimestamps(show: Boolean) = viewModelScope.launch { settingsManager.setShowTimestamps(show) }

    // AI Behavior
    val defaultSystemPrompt = settingsManager.defaultSystemPrompt.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val temperature = settingsManager.temperature.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val maxTokens = settingsManager.maxTokens.stateIn(viewModelScope, SharingStarted.Eagerly, 8192)
    val streaming = settingsManager.streaming.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val extendedThinking = settingsManager.extendedThinking.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val thinkingBudget = settingsManager.thinkingBudget.stateIn(viewModelScope, SharingStarted.Eagerly, 4000)

    fun setDefaultSystemPrompt(prompt: String) = viewModelScope.launch { settingsManager.setDefaultSystemPrompt(prompt) }
    fun setTemperature(temp: Float) = viewModelScope.launch { settingsManager.setTemperature(temp) }
    fun setMaxTokens(tokens: Int) = viewModelScope.launch { settingsManager.setMaxTokens(tokens) }
    fun setStreaming(enabled: Boolean) = viewModelScope.launch { settingsManager.setStreaming(enabled) }
    fun setExtendedThinking(enabled: Boolean) = viewModelScope.launch { settingsManager.setExtendedThinking(enabled) }
    fun setThinkingBudget(budget: Int) = viewModelScope.launch { settingsManager.setThinkingBudget(budget) }

    // Voice
    val autoReadResponses = settingsManager.enableTts.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val ttsSpeed = settingsManager.ttsSpeed.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val ttsPitch = settingsManager.ttsPitch.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    fun setAutoReadResponses(enabled: Boolean) = viewModelScope.launch { settingsManager.setEnableTts(enabled) }
    fun setTtsSpeed(speed: Float) = viewModelScope.launch { settingsManager.setTtsSpeed(speed) }
    fun setTtsPitch(pitch: Float) = viewModelScope.launch { settingsManager.setTtsPitch(pitch) }

    // Workspace
    val autoInjectContext = settingsManager.autoInjectContext.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val maxContextFiles = settingsManager.maxContextFiles.stateIn(viewModelScope, SharingStarted.Eagerly, 5)
    val autoSaveArtifacts = settingsManager.autoSaveArtifacts.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAutoInjectContext(enabled: Boolean) = viewModelScope.launch { settingsManager.setAutoInjectContext(enabled) }
    fun setMaxContextFiles(count: Int) = viewModelScope.launch { settingsManager.setMaxContextFiles(count) }
    fun setAutoSaveArtifacts(enabled: Boolean) = viewModelScope.launch { settingsManager.setAutoSaveArtifacts(enabled) }

    val isMemoryEnabled: StateFlow<Boolean> = memoryManager.isMemoryEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val memories: StateFlow<List<com.openchat.app.data.model.Memory>> = memoryManager.getAllMemories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            memoryManager.setMemoryEnabled(enabled)
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            memoryManager.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryManager.clearAllMemories()
        }
    }

    val providers: StateFlow<List<ApiProvider>> = providerRepository.getAllProviders()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val models: StateFlow<List<AiModel>> = providerRepository.getAllModels()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addProvider(name: String, url: String, apiKey: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (name.isBlank() || url.isBlank() || apiKey.isBlank()) {
            onError("All fields are required")
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            onError("URL must start with http:// or https://")
            return
        }
        if (providers.value.any { it.name.equals(name, ignoreCase = true) }) {
            onError("Provider name already exists")
            return
        }

        viewModelScope.launch {
            val provider = ApiProvider(name = name, baseUrl = url, encryptedApiKey = "", isActive = true)
            providerRepository.createProvider(provider)
            providerRepository.saveApiKey(provider.id, apiKey)
            onSuccess()
        }
    }

    fun updateProvider(provider: ApiProvider, newName: String, newUrl: String, newApiKey: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (newName.isBlank() || newUrl.isBlank()) {
            onError("Name and URL are required")
            return
        }
        if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
            onError("URL must start with http:// or https://")
            return
        }
        if (providers.value.any { it.id != provider.id && it.name.equals(newName, ignoreCase = true) }) {
            onError("Provider name already exists")
            return
        }

        viewModelScope.launch {
            providerRepository.updateProvider(provider.copy(name = newName, baseUrl = newUrl))
            if (newApiKey.isNotBlank()) {
                providerRepository.saveApiKey(provider.id, newApiKey)
            }
            onSuccess()
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            providerRepository.deleteProvider(id)
        }
    }

    fun addCustomModel(modelId: String, displayName: String, providerId: String, censorMode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (modelId.isBlank() || displayName.isBlank() || providerId.isBlank()) {
            onError("All fields are required")
            return
        }
        viewModelScope.launch {
            val model = AiModel(
                modelId = modelId,
                displayName = displayName,
                providerId = providerId,
                isBuiltIn = false,
                censorMode = censorMode
            )
            providerRepository.createModel(model)
            onSuccess()
        }
    }

    fun updateCustomModel(model: AiModel, newModelId: String, newDisplayName: String, newProviderId: String, newCensorMode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (newModelId.isBlank() || newDisplayName.isBlank() || newProviderId.isBlank()) {
            onError("All fields are required")
            return
        }
        viewModelScope.launch {
            providerRepository.updateModel(
                model.copy(
                    modelId = newModelId,
                    displayName = newDisplayName,
                    providerId = newProviderId,
                    censorMode = newCensorMode
                )
            )
            onSuccess()
        }
    }

    fun deleteCustomModel(id: String) {
        viewModelScope.launch {
            providerRepository.deleteModel(id)
        }
    }

    fun hasApiKey(providerId: String): Boolean {
        return !providerRepository.getApiKey(providerId).isNullOrBlank()
    }
}
