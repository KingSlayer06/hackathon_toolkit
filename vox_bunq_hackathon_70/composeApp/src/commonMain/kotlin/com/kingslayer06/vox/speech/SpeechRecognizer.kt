package com.kingslayer06.vox.speech

import kotlinx.coroutines.flow.StateFlow

/**
 * Hold-to-talk speech recognizer. Mirrors the React `useSpeechRecognition` hook.
 *
 * Lifecycle: [start] begins listening; intermediate hypotheses flow into
 * [interim], confirmed phrases append to [final]. [stop] ends the session.
 * Implementations must transparently restart the underlying recognizer if
 * the platform auto-stops on silence while the user is still "intending" to
 * speak (intentListening flag).
 */
interface SpeechRecognizer {
    val supported: Boolean
    val listening: StateFlow<Boolean>
    val interim: StateFlow<String>
    val final: StateFlow<String>
    val error: StateFlow<String?>

    fun start()
    fun stop()
    fun reset()
    fun dispose()
}

/** Each platform supplies its own constructor. */
expect fun createSpeechRecognizer(languageTag: String = "en-US"): SpeechRecognizer
