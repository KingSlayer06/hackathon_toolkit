package com.kingslayer06.vox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(VoxColors.Panel, RoundedCornerShape(16.dp))
            .border(1.dp, VoxColors.Line, RoundedCornerShape(16.dp)),
    ) { content() }
}

@Composable
fun PanelTight(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(VoxColors.Panel2, RoundedCornerShape(12.dp))
            .border(1.dp, VoxColors.Line, RoundedCornerShape(12.dp)),
    ) { content() }
}

@Composable
fun Chip(text: String, color: androidx.compose.ui.graphics.Color = VoxColors.Muted, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(VoxColors.Panel2, RoundedCornerShape(999.dp))
            .border(1.dp, VoxColors.Line, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text,
            color = color,
            fontWeight = FontWeight.Medium,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = VoxColors.Accent,
            contentColor = VoxColors.AccentInk,
            disabledContainerColor = VoxColors.Accent.copy(alpha = 0.4f),
            disabledContentColor = VoxColors.AccentInk.copy(alpha = 0.6f),
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = VoxColors.Ink,
            disabledContentColor = VoxColors.Muted,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, VoxColors.Line),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text)
    }
}
