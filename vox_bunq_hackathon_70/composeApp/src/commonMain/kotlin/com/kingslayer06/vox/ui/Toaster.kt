package com.kingslayer06.vox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class ToastUi(
    val id: Long,
    val variant: ToastVariant,
    val title: String,
    val detail: String? = null,
    val ttlMs: Long = 6000,
)

enum class ToastVariant { Info, Fire, Ok, Bad }

@Composable
fun Toaster(
    toasts: SnapshotStateList<ToastUi>,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            toasts.forEach { toast ->
                LaunchedEffect(toast.id) {
                    delay(toast.ttlMs)
                    onDismiss(toast.id)
                }
                AnimatedVisibility(
                    visible = true,
                    enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut(),
                ) {
                    ToastCard(toast, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun ToastCard(toast: ToastUi, onDismiss: (Long) -> Unit) {
    val (border, bg, fg) = when (toast.variant) {
        ToastVariant.Fire -> Triple(VoxColors.Accent.copy(alpha = 0.6f), VoxColors.Accent.copy(alpha = 0.1f), VoxColors.Accent)
        ToastVariant.Ok -> Triple(VoxColors.Accent.copy(alpha = 0.6f), VoxColors.Panel, VoxColors.Ink)
        ToastVariant.Bad -> Triple(VoxColors.Bad.copy(alpha = 0.6f), VoxColors.Bad.copy(alpha = 0.1f), VoxColors.Bad)
        ToastVariant.Info -> Triple(VoxColors.Line, VoxColors.Panel, VoxColors.Ink)
    }
    Box(
        Modifier
            .fillMaxWidth(0.92f)
            .background(bg, RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(glyphFor(toast.variant), color = fg, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f)) {
                Text(toast.title, color = fg, fontWeight = FontWeight.SemiBold, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                if (!toast.detail.isNullOrBlank()) {
                    Text(toast.detail, color = fg.copy(alpha = 0.8f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(onClick = { onDismiss(toast.id) }) {
                Text("×", color = VoxColors.Muted)
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") Color.Transparent
}

private fun glyphFor(v: ToastVariant): String = when (v) {
    ToastVariant.Fire -> "⚡"
    ToastVariant.Ok -> "✓"
    ToastVariant.Bad -> "⚠"
    ToastVariant.Info -> "·"
}
