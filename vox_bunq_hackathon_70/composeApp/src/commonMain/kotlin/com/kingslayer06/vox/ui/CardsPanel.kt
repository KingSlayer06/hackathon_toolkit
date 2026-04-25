package com.kingslayer06.vox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kingslayer06.vox.data.CardInfo

@Composable
fun CardsPanel(cards: List<CardInfo>?, modifier: Modifier = Modifier) {
    Panel(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Cards", count = cards?.size)
            when {
                cards == null -> SkeletonRows(2)
                cards.isEmpty() -> Text(
                    "No cards on this account.",
                    color = VoxColors.Muted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                else -> cards.forEach { CardRow(it) }
            }
            if (!cards.isNullOrEmpty()) {
                Text(
                    "Use the labels above verbatim when speaking freeze rules.",
                    color = VoxColors.Muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun CardRow(card: CardInfo) {
    val tone = statusTone(card.status)
    val frozen = tone == Tone.Bad

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MiniCard(label = card.label, type = card.type, frozen = frozen)
        Column(Modifier.weight(1f)) {
            Text(
                card.label.ifBlank { "(untitled card)" },
                color = VoxColors.Ink,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusDot(tone)
                Text(
                    prettyStatus(card.status),
                    color = toneColor(tone),
                    fontWeight = FontWeight.Medium,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
                if (!card.type.isNullOrBlank()) {
                    Text(
                        "· ${card.type.lowercase()}",
                        color = VoxColors.Muted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniCard(label: String, type: String?, frozen: Boolean) {
    val gradient = if (frozen) {
        Brush.linearGradient(listOf(Color(0xFF333339), Color(0xFF1A1A20)))
    } else {
        Brush.linearGradient(listOf(VoxColors.Accent, VoxColors.Accent2))
    }
    Box(
        Modifier
            .size(width = 80.dp, height = 50.dp)
            .background(gradient, RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (frozen) VoxColors.Bad.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            ),
    ) {
        Column(Modifier.padding(6.dp).fillMaxSize()) {
            Text(
                "BUNQ",
                fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontWeight = FontWeight.SemiBold,
                color = if (frozen) VoxColors.Muted else VoxColors.AccentInk,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label.ifBlank { "card" },
                fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontWeight = FontWeight.Bold,
                color = if (frozen) VoxColors.Muted else VoxColors.AccentInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (!type.isNullOrBlank()) {
                Text(
                    type.take(6).uppercase(),
                    fontSize = androidx.compose.ui.unit.TextUnit(7f, androidx.compose.ui.unit.TextUnitType.Sp),
                    color = if (frozen) VoxColors.Muted else VoxColors.AccentInk.copy(alpha = 0.7f),
                )
            }
        }
        if (frozen) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.AcUnit,
                    contentDescription = "Frozen",
                    tint = VoxColors.Bad,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private enum class Tone { Ok, Warn, Bad, Muted }

private fun statusTone(status: String): Tone = when (status.uppercase()) {
    "ACTIVE" -> Tone.Ok
    "DEACTIVATED", "CANCELLED", "LOST", "STOLEN" -> Tone.Bad
    "EXPIRED", "PIN_TRIES_EXCEEDED" -> Tone.Muted
    else -> Tone.Warn
}

private fun toneColor(tone: Tone): Color = when (tone) {
    Tone.Ok -> VoxColors.Accent
    Tone.Warn -> VoxColors.Warn
    Tone.Bad -> VoxColors.Bad
    Tone.Muted -> VoxColors.Muted
}

@Composable
private fun StatusDot(tone: Tone) {
    Box(
        Modifier
            .size(7.dp)
            .background(toneColor(tone), CircleShape),
    )
    Spacer(Modifier.width(0.dp))
}

private fun prettyStatus(status: String): String =
    status.replace("_", " ").lowercase()
