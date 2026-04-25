package com.kingslayer06.vox.speech

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.setActive
import platform.Foundation.NSLocale
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognitionTaskHintDictation
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus

@OptIn(ExperimentalForeignApi::class)
class IosSpeechRecognizerImpl(languageTag: String) : SpeechRecognizer {

    private val locale = NSLocale(localeIdentifier = languageTag)
    private val recognizer: SFSpeechRecognizer? =
        SFSpeechRecognizer(locale = locale)?.takeIf { it.isAvailable() }

    override val supported: Boolean = recognizer != null

    private val _listening = MutableStateFlow(false)
    override val listening: StateFlow<Boolean> = _listening
    private val _interim = MutableStateFlow("")
    override val interim: StateFlow<String> = _interim
    private val _final = MutableStateFlow("")
    override val final: StateFlow<String> = _final
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error

    private var audioEngine: AVAudioEngine? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null

    override fun start() {
        if (!supported) {
            _error.value = "Speech recognition unavailable on this device."
            return
        }
        if (_listening.value) return
        _error.value = null
        _final.value = ""
        _interim.value = ""

        SFSpeechRecognizer.requestAuthorization { status ->
            if (status != SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
                _error.value = "Speech recognition permission denied."
                return@requestAuthorization
            }
            AVAudioSession.sharedInstance().requestRecordPermission { granted ->
                if (!granted) {
                    _error.value = "Microphone permission denied."
                    return@requestRecordPermission
                }
                runCatching { beginListening() }
                    .onFailure { t ->
                        _error.value = t.message ?: "speech start failed"
                        teardown()
                    }
            }
        }
    }

    private fun beginListening() {
        teardown()

        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            category = AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeMeasurement,
            options = AVAudioSessionCategoryOptionDuckOthers,
            error = null,
        )
        session.setActive(true, error = null)

        val req = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
            taskHint = SFSpeechRecognitionTaskHintDictation
        }
        request = req

        val engine = AVAudioEngine()
        audioEngine = engine
        val input = engine.inputNode
        val format = input.outputFormatForBus(0u)
        input.installTapOnBus(
            bus = 0u,
            bufferSize = 1024u,
            format = format,
        ) { buffer: AVAudioPCMBuffer?, _ ->
            if (buffer != null) req.appendAudioPCMBuffer(buffer)
        }
        engine.prepare()
        engine.startAndReturnError(null)

        task = recognizer?.recognitionTaskWithRequest(req) { result, err ->
            if (result != null) {
                val text = result.bestTranscription.formattedString
                if (result.isFinal()) {
                    _final.value = if (_final.value.isBlank()) text else "${_final.value} $text".trim()
                    _interim.value = ""
                } else {
                    _interim.value = text
                }
            }
            val finished = err != null || result?.isFinal() == true
            if (finished) {
                if (err != null) {
                    val domain: String = err.domain ?: "NSError"
                    val code: Long = err.code
                    val msg: String = err.localizedDescription ?: "unknown"
                    val isNoSpeech =
                        msg.contains("No speech detected", ignoreCase = true) ||
                            (domain == "kAFAssistantErrorDomain" && (code == 1110L || code == 203L))
                    if (!isNoSpeech) {
                        _error.value = friendlyError(domain, code, msg)
                        println("[SpeechRecognizer.ios] error domain=$domain code=$code msg=$msg userInfo=${err.userInfo}")
                    }
                }
                teardown()
                _listening.value = false
            }
        }
        _listening.value = true
    }

    override fun stop() {
        runCatching { request?.endAudio() }
        runCatching { audioEngine?.inputNode?.removeTapOnBus(0u) }
        runCatching { audioEngine?.stop() }
    }

    private fun teardown() {
        runCatching { audioEngine?.inputNode?.removeTapOnBus(0u) }
        runCatching { audioEngine?.stop() }
        runCatching { request?.endAudio() }
        runCatching { task?.cancel() }
        runCatching { AVAudioSession.sharedInstance().setActive(false, error = null) }
        audioEngine = null
        request = null
        task = null
    }

    override fun reset() {
        _final.value = ""
        _interim.value = ""
        _error.value = null
    }

    override fun dispose() {
        teardown()
        _listening.value = false
    }

    private fun friendlyError(domain: String, code: Long, msg: String): String = when {
        domain == "kLSRErrorDomain" && code == 300L ->
            "iOS simulator has no on-device speech model installed. Test on a real device, or open Simulator → Settings → General → Keyboard → Enable Dictation and wait for the language download."
        domain == "kAFAssistantErrorDomain" && code == 1101L ->
            "Speech recognition asset not available. Try a different locale or test on a real device."
        domain == "kAFAssistantErrorDomain" && code == 1700L ->
            "Speech permission was revoked. Re-enable in Settings → Privacy → Speech Recognition."
        else -> "speech: $msg [$domain:$code]"
    }
}

actual fun createSpeechRecognizer(languageTag: String): SpeechRecognizer =
    IosSpeechRecognizerImpl(languageTag)
