package com.kingslayer06.vox.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import com.kingslayer06.vox.platform.AppContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val IGNORED_ERRORS = setOf(
    AndroidSpeechRecognizer.ERROR_NO_MATCH,
    AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    AndroidSpeechRecognizer.ERROR_CLIENT,
)

class AndroidSpeechRecognizerImpl(
    private val context: Context,
    private val languageTag: String,
) : SpeechRecognizer {

    override val supported: Boolean = AndroidSpeechRecognizer.isRecognitionAvailable(context)
    private val _listening = MutableStateFlow(false)
    override val listening: StateFlow<Boolean> = _listening
    private val _interim = MutableStateFlow("")
    override val interim: StateFlow<String> = _interim
    private val _final = MutableStateFlow("")
    override val final: StateFlow<String> = _final
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error

    private var rec: AndroidSpeechRecognizer? = null
    /** True while the user is intentionally holding the mic. */
    private var intentListening = false

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _interim.value = text
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                _final.value = (_final.value + " " + text).trim()
            }
            _interim.value = ""
            if (intentListening) restart() else _listening.value = false
        }

        override fun onError(error: Int) {
            if (error in IGNORED_ERRORS) {
                if (intentListening) restart() else _listening.value = false
                return
            }
            _error.value = errorMessage(error)
            intentListening = false
            _listening.value = false
        }
    }

    override fun start() {
        if (!supported) {
            _error.value = "Speech recognition unavailable on this device."
            return
        }
        if (intentListening) return
        _error.value = null
        _final.value = ""
        _interim.value = ""
        intentListening = true
        ensureRecognizer()
        try {
            rec?.startListening(intent)
            _listening.value = true
        } catch (t: Throwable) {
            _error.value = t.message ?: "speech start failed"
            intentListening = false
            _listening.value = false
        }
    }

    override fun stop() {
        intentListening = false
        try { rec?.stopListening() } catch (_: Throwable) {}
        _listening.value = false
    }

    override fun reset() {
        _final.value = ""
        _interim.value = ""
        _error.value = null
    }

    override fun dispose() {
        intentListening = false
        try { rec?.cancel() } catch (_: Throwable) {}
        try { rec?.destroy() } catch (_: Throwable) {}
        rec = null
    }

    private fun ensureRecognizer() {
        if (rec == null) {
            rec = AndroidSpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }
    }

    private fun restart() {
        try {
            rec?.cancel()
            rec?.startListening(intent)
        } catch (_: Throwable) { /* swallow */ }
    }

    private fun errorMessage(code: Int): String = when (code) {
        AndroidSpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
        AndroidSpeechRecognizer.ERROR_NETWORK,
        AndroidSpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error reaching the recognizer."
        AndroidSpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy — try again."
        AndroidSpeechRecognizer.ERROR_SERVER -> "Speech server error."
        else -> "Speech error ($code)."
    }
}

actual fun createSpeechRecognizer(languageTag: String): SpeechRecognizer =
    AndroidSpeechRecognizerImpl(AppContext.get(), languageTag)
