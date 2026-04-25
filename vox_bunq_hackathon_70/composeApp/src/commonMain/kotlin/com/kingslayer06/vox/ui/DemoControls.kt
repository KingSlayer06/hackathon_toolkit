package com.kingslayer06.vox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kingslayer06.vox.data.VoxApi
import kotlinx.coroutines.launch

@Composable
fun DemoControls(
    api: VoxApi,
    onFired: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }

    fun run(key: String, label: String, fn: suspend () -> Unit) {
        busy = key
        scope.launch {
            try { fn(); onFired(label) }
            catch (t: Throwable) { onFired("Failed: ${t.message}") }
            finally { busy = null }
        }
    }

    Panel(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Demo triggers")
            Text(
                "For the kicker beats. These hit bunq sandbox so your rules fire live.",
                color = VoxColors.Muted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
            GhostButton(
                text = if (busy == "salary") "…" else "💸  Fire incoming salary (€2000)",
                onClick = { run("salary", "Pulled €2000 'SALARY APRIL' into Main") { api.fireSalary() } },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
            )
            GhostButton(
                text = if (busy == "bar") "…" else "🍷  Fire bar payment (€35)",
                onClick = { run("bar", "Spent €35 'BAR' from Entertainment") { api.fireBarSpend() } },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
            )
            GhostButton(
                text = if (busy == "large") "…" else "🛡️  Fire large transaction (€500)",
                onClick = { run("large", "Spent €500 'LARGE PURCHASE' from Main") { api.fireLargeTx() } },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
