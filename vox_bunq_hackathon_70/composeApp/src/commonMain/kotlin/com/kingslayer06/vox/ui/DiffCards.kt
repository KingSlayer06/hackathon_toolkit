package com.kingslayer06.vox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kingslayer06.vox.data.Action
import com.kingslayer06.vox.data.ExecutionResult
import com.kingslayer06.vox.data.SubAccount

@Composable
fun DiffCards(
    actions: List<Action>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    results: Map<Int, ExecutionResult>,
    accounts: List<SubAccount>,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.forEachIndexed { i, action ->
            DiffCard(
                action = action,
                index = i,
                checked = selected.contains(i),
                onToggle = { onToggle(i) },
                result = results[i],
                accounts = accounts,
                disabled = busy,
            )
        }
    }
}

@Composable
private fun DiffCard(
    action: Action,
    index: Int,
    checked: Boolean,
    onToggle: () -> Unit,
    result: ExecutionResult?,
    accounts: List<SubAccount>,
    disabled: Boolean,
) {
    val ranOk = result?.ok == true
    val ranBad = result?.ok == false
    val borderColor = when {
        ranOk -> VoxColors.Accent.copy(alpha = 0.6f)
        ranBad -> VoxColors.Bad.copy(alpha = 0.6f)
        else -> VoxColors.Line
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(VoxColors.Panel, RoundedCornerShape(16.dp))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggle() },
                    enabled = !disabled && result == null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = VoxColors.Accent,
                        uncheckedColor = VoxColors.Line,
                        checkmarkColor = VoxColors.AccentInk,
                    ),
                )
                Chip(text = labelFor(action), color = VoxColors.Accent)
                Text(
                    "action ${index + 1}",
                    color = VoxColors.Muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (result != null) {
                    Chip(
                        text = if (ranOk) "executed" else "failed",
                        color = if (ranOk) VoxColors.Accent else VoxColors.Bad,
                    )
                }
            }

            when (action) {
                is Action.Transfer -> TransferBody(action, accounts)
                is Action.RecurringSplit -> RecurringBody(action)
                is Action.ConditionalFreeze -> ConditionalBody(action)
                is Action.TransactionLimitFreeze -> TxLimitBody(action)
            }

            if (result != null) {
                Text(
                    result.detail,
                    color = if (ranOk) VoxColors.Accent else VoxColors.Bad,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun TransferBody(action: Action.Transfer, accounts: List<SubAccount>) {
    val src = matchAccount(accounts, action.from_account)
    val dst = matchAccount(accounts, action.to_account)
    val srcAfter = (src?.balance_eur ?: 0.0) - action.amount_eur
    val dstAfter = (dst?.balance_eur ?: 0.0) + action.amount_eur

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BalanceBlock(
            label = action.from_account,
            before = src?.balance_eur ?: 0.0,
            after = srcAfter,
            color = src?.color,
            negative = true,
            unknown = src == null,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .background(VoxColors.Accent.copy(alpha = 0.1f), CircleShape)
                .border(1.dp, VoxColors.Accent.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                "€${formatEur(action.amount_eur)} →",
                color = VoxColors.Accent,
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
        BalanceBlock(
            label = action.to_account,
            before = dst?.balance_eur ?: 0.0,
            after = dstAfter,
            color = dst?.color,
            negative = false,
            unknown = dst == null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BalanceBlock(
    label: String,
    before: Double,
    after: Double,
    color: String?,
    negative: Boolean,
    unknown: Boolean,
    modifier: Modifier = Modifier,
) {
    PanelTight(modifier = modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(parseHexColor(color), CircleShape))
                Text(
                    label,
                    color = VoxColors.Muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unknown) Text("(?)", color = VoxColors.Bad, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
            Text(
                "€${formatEur(before)}",
                color = VoxColors.Muted,
                textDecoration = TextDecoration.LineThrough,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
            Text(
                "€${formatEur(after)}",
                color = if (negative) VoxColors.Warn else VoxColors.Accent,
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun RecurringBody(action: Action.RecurringSplit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PanelTight(modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("when incoming matches", color = VoxColors.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Text(
                    "\u201C${action.trigger_match}\u201D",
                    color = VoxColors.Ink,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier
                .background(VoxColors.Accent.copy(alpha = 0.1f), CircleShape)
                .border(1.dp, VoxColors.Accent.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                "${action.percentage}%",
                color = VoxColors.Accent,
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
        PanelTight(modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("route to", color = VoxColors.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Text(
                    action.to_account,
                    color = VoxColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ConditionalBody(action: Action.ConditionalFreeze) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PanelTight(modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "if spend on \u201C${action.merchant_match}\u201D over ${action.window_days}d >",
                    color = VoxColors.Muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "€${formatEur(action.threshold_eur)}",
                        color = VoxColors.Warn,
                        fontWeight = FontWeight.SemiBold,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                    Text("freeze trigger", color = VoxColors.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(VoxColors.Line, RoundedCornerShape(999.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.3f)
                            .height(6.dp)
                            .background(
                                Brush.horizontalGradient(listOf(VoxColors.Accent, VoxColors.Warn, VoxColors.Bad)),
                                RoundedCornerShape(999.dp),
                            ),
                    )
                }
            }
        }
        CardPreview(action.card_label)
    }
}

@Composable
private fun TxLimitBody(action: Action.TransactionLimitFreeze) {
    val scopes = buildList {
        if (!action.from_account.isNullOrBlank()) add("from ${action.from_account}")
        if (!action.merchant_match.isNullOrBlank()) add("matching \u201C${action.merchant_match}\u201D")
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PanelTight(modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "if any single transaction" + (if (scopes.isNotEmpty()) " " + scopes.joinToString(" · ") else "") + " ≥",
                    color = VoxColors.Muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "€${formatEur(action.max_tx_eur)}",
                        color = VoxColors.Warn,
                        fontWeight = FontWeight.SemiBold,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                    Text("freeze immediately", color = VoxColors.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
                Text("security guardrail · one-shot", color = VoxColors.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }
        CardPreview(action.card_label)
    }
}

@Composable
private fun CardPreview(label: String) {
    Box(
        Modifier
            .size(width = 110.dp, height = 70.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF333339), Color(0xFF1A1A20))),
                RoundedCornerShape(12.dp),
            )
            .border(1.dp, VoxColors.Line, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Column {
            Text("BUNQ · CARD", color = VoxColors.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            Text(label, color = VoxColors.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
            Text("will lock", color = VoxColors.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
    androidx.compose.foundation.layout.Spacer(Modifier.width(0.dp))
}

private fun matchAccount(accounts: List<SubAccount>, name: String): SubAccount? {
    val t = name.trim().lowercase()
    return accounts.firstOrNull { it.description.lowercase() == t }
        ?: accounts.firstOrNull { it.description.lowercase().contains(t) }
}

private fun labelFor(action: Action): String = when (action) {
    is Action.Transfer -> "transfer"
    is Action.RecurringSplit -> "recurring rule"
    is Action.ConditionalFreeze -> "guardrail"
    is Action.TransactionLimitFreeze -> "tx limit"
}
