package com.openchat.app.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceInputManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var wasStoppedProgrammatically = false

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript

    private val _finalTranscript = MutableStateFlow("")
    val finalTranscript: StateFlow<String> = _finalTranscript

    private val _isListeningState = MutableStateFlow(false)
    val isListeningState: StateFlow<Boolean> = _isListeningState

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState

    // TTS
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false

    val autoReadResponses: Flow<Boolean> = settingsManager.enableTts
    val ttsSpeed: Flow<Float> = settingsManager.ttsSpeed
    val ttsPitch: Flow<Float> = settingsManager.ttsPitch

    init {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.getDefault())
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsInitialized = true
            }
        }
    }

    suspend fun setAutoReadResponses(enabled: Boolean) {
        settingsManager.setEnableTts(enabled)
    }

    suspend fun setTtsSpeed(speed: Float) {
        settingsManager.setTtsSpeed(speed)
        textToSpeech?.setSpeechRate(speed)
    }

    suspend fun setTtsPitch(pitch: Float) {
        settingsManager.setTtsPitch(pitch)
        textToSpeech?.setPitch(pitch)
    }

    fun speak(text: String, speed: Float = 1.0f, pitch: Float = 1.0f) {
        if (ttsInitialized) {
            textToSpeech?.setSpeechRate(speed)
            textToSpeech?.setPitch(pitch)
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_response")
        }
    }

    fun stopSpeaking() {
        if (ttsInitialized) {
            textToSpeech?.stop()
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _errorState.value = "Speech recognition is not available on this device"
            return
        }

        wasStoppedProgrammatically = false
        _partialTranscript.value = ""
        _finalTranscript.value = ""
        _errorState.value = null

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@VoiceInputManager)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L) // 2 second timeout
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            _isListeningState.value = true
        } catch (e: Exception) {
            _errorState.value = "Failed to start listening: ${e.message}"
            isListening = false
            _isListeningState.value = false
        }
    }

    fun stopListening() {
        wasStoppedProgrammatically = true
        speechRecognizer?.stopListening()
        isListening = false
        _isListeningState.value = false
    }

    fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    override fun onReadyForSpeech(params: Bundle?) {}

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {
        if (!wasStoppedProgrammatically) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                SpeechRecognizer.ERROR_SERVER -> "Error from server"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Didn't understand, please try again."
            }
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                // Auto-stopped on silence, just turn off
                isListening = false
                _isListeningState.value = false
            } else {
                _errorState.value = message
                isListening = false
                _isListeningState.value = false
            }
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            _finalTranscript.value = matches[0]
            _partialTranscript.value = ""
        }
        isListening = false
        _isListeningState.value = false
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            _partialTranscript.value = matches[0]
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
