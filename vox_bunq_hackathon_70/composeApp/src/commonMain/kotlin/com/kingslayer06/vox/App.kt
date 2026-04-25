package com.kingslayer06.vox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.kingslayer06.vox.data.VoxApi
import com.kingslayer06.vox.speech.createSpeechRecognizer
import com.kingslayer06.vox.ui.MainScreen
import com.kingslayer06.vox.ui.VoxTheme

@Composable
fun App() {
    VoxTheme {
        val api = remember { VoxApi() }
        val speech = remember { createSpeechRecognizer("en-US") }
        DisposableEffect(speech) { onDispose { speech.dispose() } }
        MainScreen(api = api, speech = speech)
    }
}
