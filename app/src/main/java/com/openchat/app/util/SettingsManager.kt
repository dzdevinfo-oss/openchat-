package com.openchat.app.util

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsDataStore by preferencesDataStore(name = "openchat_settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Appearance
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val fontSizeKey = floatPreferencesKey("font_size")
    private val messageBubbleStyleKey = stringPreferencesKey("message_bubble_style")
    private val showTimestampsKey = booleanPreferencesKey("show_timestamps")

    // AI Behavior
    private val defaultSystemPromptKey = stringPreferencesKey("default_system_prompt")
    private val temperatureKey = floatPreferencesKey("temperature")
    private val maxTokensKey = intPreferencesKey("max_tokens")
    private val streamingKey = booleanPreferencesKey("streaming_enabled")
    private val extendedThinkingKey = booleanPreferencesKey("extended_thinking")
    private val thinkingBudgetKey = intPreferencesKey("thinking_budget")

    // Voice
    private val enableVoiceInputKey = booleanPreferencesKey("enable_voice_input")
    private val autoSendVoiceKey = booleanPreferencesKey("auto_send_voice")
    private val enableTtsKey = booleanPreferencesKey("enable_tts")
    private val ttsSpeedKey = floatPreferencesKey("tts_speed")
    private val ttsPitchKey = floatPreferencesKey("tts_pitch")

    // Workspace
    private val autoInjectContextKey = booleanPreferencesKey("auto_inject_context")
    private val maxContextFilesKey = intPreferencesKey("max_context_files")
    private val autoSaveArtifactsKey = booleanPreferencesKey("auto_save_artifacts")
    
    // System
    private val isFirstLaunchKey = booleanPreferencesKey("is_first_launch")
    val autoInjectWorkspaceContext = autoInjectContextKey // Alias for ChatViewModel

    val darkTheme: Flow<Boolean> = context.appSettingsDataStore.data.map { it[darkThemeKey] ?: true }
    val fontSize: Flow<Float> = context.appSettingsDataStore.data.map { it[fontSizeKey] ?: 16f }
    val messageBubbleStyle: Flow<String> = context.appSettingsDataStore.data.map { it[messageBubbleStyleKey] ?: "Modern" }
    val showTimestamps: Flow<Boolean> = context.appSettingsDataStore.data.map { it[showTimestampsKey] ?: true }

    val defaultSystemPrompt: Flow<String> = context.appSettingsDataStore.data.map { it[defaultSystemPromptKey] ?: "" }
    val temperature: Flow<Float> = context.appSettingsDataStore.data.map { it[temperatureKey] ?: 1.0f }
    val maxTokens: Flow<Int> = context.appSettingsDataStore.data.map { it[maxTokensKey] ?: 8192 }
    val streaming: Flow<Boolean> = context.appSettingsDataStore.data.map { it[streamingKey] ?: true }
    val extendedThinking: Flow<Boolean> = context.appSettingsDataStore.data.map { it[extendedThinkingKey] ?: false }
    val thinkingBudget: Flow<Int> = context.appSettingsDataStore.data.map { it[thinkingBudgetKey] ?: 4000 }

    val enableVoiceInput: Flow<Boolean> = context.appSettingsDataStore.data.map { it[enableVoiceInputKey] ?: true }
    val autoSendVoice: Flow<Boolean> = context.appSettingsDataStore.data.map { it[autoSendVoiceKey] ?: false }
    val enableTts: Flow<Boolean> = context.appSettingsDataStore.data.map { it[enableTtsKey] ?: false }
    val ttsSpeed: Flow<Float> = context.appSettingsDataStore.data.map { it[ttsSpeedKey] ?: 1.0f }
    val ttsPitch: Flow<Float> = context.appSettingsDataStore.data.map { it[ttsPitchKey] ?: 1.0f }

    val autoInjectContext: Flow<Boolean> = context.appSettingsDataStore.data.map { it[autoInjectContextKey] ?: true }
    val maxContextFiles: Flow<Int> = context.appSettingsDataStore.data.map { it[maxContextFilesKey] ?: 5 }
    val autoSaveArtifacts: Flow<Boolean> = context.appSettingsDataStore.data.map { it[autoSaveArtifactsKey] ?: false }
    val isFirstLaunch: Flow<Boolean> = context.appSettingsDataStore.data.map { it[isFirstLaunchKey] ?: true }

    suspend fun setDarkTheme(enabled: Boolean) = context.appSettingsDataStore.edit { it[darkThemeKey] = enabled }
    suspend fun setFontSize(size: Float) = context.appSettingsDataStore.edit { it[fontSizeKey] = size }
    suspend fun setMessageBubbleStyle(style: String) = context.appSettingsDataStore.edit { it[messageBubbleStyleKey] = style }
    suspend fun setShowTimestamps(show: Boolean) = context.appSettingsDataStore.edit { it[showTimestampsKey] = show }

    suspend fun setDefaultSystemPrompt(prompt: String) = context.appSettingsDataStore.edit { it[defaultSystemPromptKey] = prompt }
    suspend fun setTemperature(temp: Float) = context.appSettingsDataStore.edit { it[temperatureKey] = temp }
    suspend fun setMaxTokens(tokens: Int) = context.appSettingsDataStore.edit { it[maxTokensKey] = tokens }
    suspend fun setStreaming(enabled: Boolean) = context.appSettingsDataStore.edit { it[streamingKey] = enabled }
    suspend fun setExtendedThinking(enabled: Boolean) = context.appSettingsDataStore.edit { it[extendedThinkingKey] = enabled }
    suspend fun setThinkingBudget(budget: Int) = context.appSettingsDataStore.edit { it[thinkingBudgetKey] = budget }

    suspend fun setEnableVoiceInput(enabled: Boolean) = context.appSettingsDataStore.edit { it[enableVoiceInputKey] = enabled }
    suspend fun setAutoSendVoice(enabled: Boolean) = context.appSettingsDataStore.edit { it[autoSendVoiceKey] = enabled }
    suspend fun setEnableTts(enabled: Boolean) = context.appSettingsDataStore.edit { it[enableTtsKey] = enabled }
    suspend fun setTtsSpeed(speed: Float) = context.appSettingsDataStore.edit { it[ttsSpeedKey] = speed }
    suspend fun setTtsPitch(pitch: Float) = context.appSettingsDataStore.edit { it[ttsPitchKey] = pitch }

    suspend fun setAutoInjectContext(enabled: Boolean) = context.appSettingsDataStore.edit { it[autoInjectContextKey] = enabled }
    suspend fun setMaxContextFiles(count: Int) = context.appSettingsDataStore.edit { it[maxContextFilesKey] = count }
    suspend fun setAutoSaveArtifacts(enabled: Boolean) = context.appSettingsDataStore.edit { it[autoSaveArtifactsKey] = enabled }
    suspend fun setFirstLaunchCompleted() = context.appSettingsDataStore.edit { it[isFirstLaunchKey] = false }
}
