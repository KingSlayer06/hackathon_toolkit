package com.kingslayer06.vox.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun MicButton(
    listening: Boolean,
    supported: Boolean,
    enabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "mic-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-alpha",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .pointerInput(enabled, supported) {
                    if (!enabled || !supported) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            onStart()
                            try { tryAwaitRelease() } finally { onStop() }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (listening) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulse)
                        .alpha(pulseAlpha)
                        .background(VoxColors.Accent.copy(alpha = 0.45f), CircleShape),
                )
            }
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        color = if (listening) VoxColors.Accent else VoxColors.Panel,
                        shape = CircleShape,
                    )
                    .border(
                        width = if (listening) 4.dp else 1.dp,
                        color = if (listening) VoxColors.Accent.copy(alpha = 0.7f) else VoxColors.Line,
                        shape = CircleShape,
                    )
                    .alpha(if (enabled && supported) 1f else 0.4f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = if (listening) "Recording" else "Hold to speak",
                    modifier = Modifier.size(56.dp),
                    tint = if (listening) VoxColors.AccentInk else VoxColors.Accent,
                )
            }
        }

        val label = when {
            !supported -> "Speech recognition unavailable"
            listening -> "Listening… release to plan"
            !enabled -> "Working…"
            else -> "Hold the button and speak"
        }
        Text(label, color = VoxColors.Muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
    }
    Box(modifier = Modifier.height(0.dp)) {} // spacer placeholder
}
