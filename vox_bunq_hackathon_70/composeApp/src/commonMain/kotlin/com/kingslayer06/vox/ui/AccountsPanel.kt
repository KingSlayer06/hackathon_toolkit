package com.kingslayer06.vox.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kingslayer06.vox.data.SubAccount

@Composable
fun AccountsPanel(
    accounts: List<SubAccount>?,
    highlight: Set<Long>,
    modifier: Modifier = Modifier,
) {
    Panel(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Sub-accounts", count = accounts?.size)

            when {
                accounts == null -> SkeletonRows(4)
                accounts.isEmpty() -> Text(
                    "No sub-accounts. Run the demo setup first.",
                    color = VoxColors.Muted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                else -> accounts.forEach { account ->
                    AccountRow(account, hot = highlight.contains(account.id))
                }
            }
        }
    }
}

@Composable
private fun AccountRow(account: SubAccount, hot: Boolean) {
    val borderColor by animateColorAsState(
        targetValue = if (hot) VoxColors.Accent else VoxColors.Line,
        label = "acct-border",
    )
    PanelTight(modifier = Modifier.fillMaxWidth().border(2.dp, borderColor, RoundedCornerShape(12.dp))) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(parseHexColor(account.color), CircleShape),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    account.description,
                    color = VoxColors.Ink,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                if (!account.iban.isNullOrBlank()) {
                    Text(
                        account.iban,
                        color = VoxColors.Muted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "€${formatEur(account.balance_eur)}",
                color = VoxColors.Ink,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, count: Int? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            color = VoxColors.Muted,
            fontWeight = FontWeight.SemiBold,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
        if (count != null) {
            Text(
                count.toString(),
                color = VoxColors.Muted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun SkeletonRows(n: Int) {
    repeat(n) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(VoxColors.Panel2.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .border(1.dp, VoxColors.Line, RoundedCornerShape(12.dp)),
        )
    }
}
