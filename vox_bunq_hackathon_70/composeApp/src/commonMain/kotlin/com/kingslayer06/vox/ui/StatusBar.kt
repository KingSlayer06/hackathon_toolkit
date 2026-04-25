package com.kingslayer06.vox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingslayer06.vox.data.Health

@Composable
fun StatusBar(
    health: Health?,
    sseConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill(ok = health?.bunq_authenticated == true, label = "bunq " + (if (health?.bunq_authenticated == true) "ok" else "down"))
        Pill(ok = !health?.llm_provider.isNullOrBlank(), label = "llm " + (health?.llm_provider ?: "—"))
        Pill(ok = sseConnected, label = "events " + (if (sseConnected) "live" else "offline"))
    }
}

@Composable
private fun Pill(ok: Boolean, label: String) {
    val color = if (ok) VoxColors.Accent else VoxColors.Bad
    Row(
        Modifier
            .background(VoxColors.Panel2, RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, color = color, fontWeight = FontWeight.Medium, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
    @Suppress("UNUSED_EXPRESSION") Color.Transparent
}
