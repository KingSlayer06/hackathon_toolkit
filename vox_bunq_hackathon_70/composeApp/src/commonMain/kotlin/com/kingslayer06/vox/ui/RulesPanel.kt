package com.kingslayer06.vox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kingslayer06.vox.data.RuleView
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Composable
fun RulesPanel(
    rules: List<RuleView>?,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Active rules", count = rules?.size)
            if (rules.isNullOrEmpty()) {
                Text(
                    "No rules armed. Speak a recurring or conditional command to add one.",
                    color = VoxColors.Muted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            } else {
                rules.forEach { rule -> RuleRow(rule, onDelete) }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: RuleView, onDelete: (Long) -> Unit) {
    PanelTight(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(text = "● " + ruleKindLabel(rule.kind), color = VoxColors.Accent)
                if (rule.fired_count > 0) {
                    Chip(text = "fired ${rule.fired_count}×", color = VoxColors.Accent)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                TextButton(onClick = { onDelete(rule.id) }) {
                    Text(
                        "disarm",
                        color = VoxColors.Bad,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                rule.summary,
                color = VoxColors.Ink,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            Text(
                "#${rule.id} · armed ${timeAgo(rule.created_at)}",
                color = VoxColors.Muted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun ruleKindLabel(kind: String): String = when (kind) {
    "recurring_split" -> "recurring"
    "conditional_freeze" -> "guardrail"
    "transaction_limit_freeze" -> "tx limit"
    else -> kind
}

private fun timeAgo(iso: String): String = try {
    val normalized = if (iso.endsWith("Z")) iso else "${iso}Z"
    val t = Instant.parse(normalized)
    val diff = (Clock.System.now() - t).inWholeSeconds
    when {
        diff < 60 -> "${diff}s ago"
        diff < 3600 -> "${diff / 60}m ago"
        else -> "${diff / 3600}h ago"
    }
} catch (_: Throwable) { iso }
