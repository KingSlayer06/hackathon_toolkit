package com.kingslayer06.vox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kingslayer06.vox.data.ExecutionResult
import com.kingslayer06.vox.data.Health
import com.kingslayer06.vox.data.Plan
import com.kingslayer06.vox.data.VoxApi
import com.kingslayer06.vox.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun MainScreen(api: VoxApi, speech: SpeechRecognizer) {
    val scope = rememberCoroutineScope()

    var health by remember { mutableStateOf<Health?>(null) }
    var sseConnected by remember { mutableStateOf(false) }

    val accounts = pollState(intervalMs = 4000) { api.accounts() }
    val cards = pollState(intervalMs = 8000) { api.cards() }
    val rules = pollState(intervalMs = 4000) { api.rules() }

    var plan by remember { mutableStateOf<Plan?>(null) }
    var planError by remember { mutableStateOf<String?>(null) }
    var planning by remember { mutableStateOf(false) }
    var executing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    val results = remember { mutableStateOf<Map<Int, ExecutionResult>>(emptyMap()) }

    val toasts = remember { mutableStateListOf<ToastUi>() }
    var nextToastId by remember { mutableStateOf(1L) }
    fun pushToast(t: ToastUi) {
        val id = nextToastId++
        toasts.add(t.copy(id = id))
    }

    val hotAccounts = remember { mutableStateOf(emptySet<Long>()) }
    fun flashAccount(id: Long) {
        scope.launch {
            hotAccounts.value = hotAccounts.value + id
            delay(2500)
            hotAccounts.value = hotAccounts.value - id
        }
    }

    val listening by speech.listening.collectAsState()
    val interim by speech.interim.collectAsState()
    val finalText by speech.final.collectAsState()
    val speechError by speech.error.collectAsState()

    LaunchedEffect(Unit) {
        runCatching { health = api.health() }.onFailure { health = null }
    }

    // SSE — listen for live rule firings
    LaunchedEffect(Unit) {
        api.firingStream()
            .retryWhen { _, attempt -> sseConnected = false; delay(2_000); true }
            .catch { sseConnected = false }
            .collect { ev ->
                sseConnected = true
                pushToast(ToastUi(0, ToastVariant.Fire, "Rule fired · #${ev.rule_id}", ev.summary))
                accounts.refresh()
                rules.refresh()
                cards.refresh()
            }
    }

    // Auto-submit on release if user spoke something substantial
    val lastFinal = remember { mutableStateOf("") }
    LaunchedEffect(finalText, listening, planning, executing) {
        if (listening || planning || executing) return@LaunchedEffect
        val t = finalText.trim()
        if (t.isEmpty() || t == lastFinal.value || t.length < 8) return@LaunchedEffect
        lastFinal.value = t
        planning = true
        planError = null
        plan = null
        results.value = emptyMap()
        runCatching { api.plan(t) }
            .onSuccess { p ->
                plan = p
                selected = p.actions.indices.toSet()
            }
            .onFailure { planError = it.message ?: it.toString() }
        planning = false
    }

    fun submit(text: String) {
        scope.launch(Dispatchers.Default) {
            planning = true
            planError = null
            plan = null
            results.value = emptyMap()
            runCatching { api.plan(text) }
                .onSuccess { p ->
                    plan = p
                    selected = p.actions.indices.toSet()
                }
                .onFailure { planError = it.message ?: it.toString() }
            planning = false
        }
    }

    fun execute() {
        val current = plan ?: return
        scope.launch {
            executing = true
            runCatching {
                api.execute(current, selected.sorted())
            }.onSuccess { resp ->
                val newMap = results.value.toMutableMap()
                resp.results.forEach { newMap[it.action_index] = it }
                results.value = newMap
                accounts.refresh(); rules.refresh(); cards.refresh()
                val allOk = resp.results.all { it.ok }
                pushToast(
                    ToastUi(
                        id = 0,
                        variant = if (allOk) ToastVariant.Ok else ToastVariant.Bad,
                        title = if (allOk) "Executed ${resp.results.size} action${if (resp.results.size == 1) "" else "s"}"
                                else "Some actions failed (${resp.results.count { !it.ok }})",
                        detail = current.summary,
                    ),
                )
            }.onFailure { t ->
                pushToast(ToastUi(0, ToastVariant.Bad, "Execute failed", t.message))
            }
            executing = false
        }
    }

    fun reset() {
        plan = null
        results.value = emptyMap()
        selected = emptySet()
        planError = null
        speech.reset()
        lastFinal.value = ""
    }

    Box(Modifier.fillMaxSize().background(VoxColors.Bg)) {
        val scroll = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderRow(health, sseConnected)
            HeroSection()
            MicButton(
                listening = listening,
                supported = speech.supported,
                enabled = !planning && !executing,
                onStart = speech::start,
                onStop = speech::stop,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            TranscriptCard(
                text = finalText,
                interim = interim,
                listening = listening,
                busy = planning,
                onSubmit = ::submit,
                onClear = ::reset,
            )
            speechError?.let { msg ->
                Panel(Modifier.fillMaxWidth().border(1.dp, VoxColors.Bad.copy(alpha = 0.6f), RoundedCornerShape(16.dp))) {
                    Text(
                        "🎙️ $msg",
                        modifier = Modifier.padding(12.dp),
                        color = VoxColors.Bad,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            planError?.let { msg ->
                Panel(Modifier.fillMaxWidth().border(1.dp, VoxColors.Bad.copy(alpha = 0.6f), RoundedCornerShape(16.dp))) {
                    Text(msg, modifier = Modifier.padding(12.dp), color = VoxColors.Bad)
                }
            }
            AnimatedVisibility(plan != null, enter = fadeIn(), exit = fadeOut()) {
                plan?.let { p ->
                    PlanSection(
                        plan = p,
                        selected = selected,
                        onToggle = { i ->
                            selected = if (selected.contains(i)) selected - i else selected + i
                        },
                        results = results.value,
                        accounts = accounts.value ?: emptyList(),
                        executing = executing,
                        onCancel = ::reset,
                        onExecute = ::execute,
                    )
                }
            }
            AccountsPanel(accounts.value, hotAccounts.value)
            CardsPanel(cards.value)
            RulesPanel(rules.value, onDelete = { id ->
                scope.launch {
                    runCatching { api.deleteRule(id) }
                    rules.refresh()
                    pushToast(ToastUi(0, ToastVariant.Info, "Disarmed rule #$id"))
                }
            })
            DemoControls(api = api, onFired = { msg ->
                pushToast(ToastUi(0, ToastVariant.Info, "Demo trigger", msg))
            })
            HintCard()
            Spacer(Modifier.height(48.dp))
        }
        Toaster(toasts = toasts, onDismiss = { id -> toasts.removeAll { it.id == id } })
    }
}

@Composable
private fun HeaderRow(health: Health?, sseConnected: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(VoxColors.Accent, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("b", color = VoxColors.AccentInk, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)
            }
            Column {
                Text("Vox", color = VoxColors.Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("talk to your bunq", color = VoxColors.Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        StatusBar(health, sseConnected)
    }
}

@Composable
private fun HeroSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Tell your money what to do.",
            color = VoxColors.Ink,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Hold the mic, speak in plain English. Vox plans the bunq sub-account moves, recurring splits, and guardrails — you approve the diff before a single euro shifts.",
            color = VoxColors.Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PlanSection(
    plan: Plan,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    results: Map<Int, ExecutionResult>,
    accounts: List<com.kingslayer06.vox.data.SubAccount>,
    executing: Boolean,
    onCancel: () -> Unit,
    onExecute: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                buildAnnotatedSummary(plan.summary),
                color = VoxColors.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onCancel) {
                Text("discard", color = VoxColors.Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (plan.warnings.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(VoxColors.Warn.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .border(1.dp, VoxColors.Warn.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Column { plan.warnings.forEach { Text("⚠ $it", color = VoxColors.Warn, style = MaterialTheme.typography.labelSmall) } }
            }
        }
        if (plan.actions.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(VoxColors.Panel2, RoundedCornerShape(12.dp))
                    .border(1.dp, VoxColors.Line, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Text("Nothing actionable — try rephrasing.", color = VoxColors.Muted)
            }
        } else {
            DiffCards(
                actions = plan.actions,
                selected = selected,
                onToggle = onToggle,
                results = results,
                accounts = accounts,
                busy = executing,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${selected.size}/${plan.actions.size} selected",
                    color = VoxColors.Muted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GhostButton("Cancel", onClick = onCancel, enabled = !executing)
                    PrimaryButton(
                        text = if (executing) "Executing…" else "Execute ${selected.size} action${if (selected.size == 1) "" else "s"}",
                        onClick = onExecute,
                        enabled = !executing && selected.isNotEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
private fun buildAnnotatedSummary(summary: String): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = VoxColors.Ink, fontWeight = FontWeight.Medium)) {
            append("Vox understood: ")
        }
        append(summary)
    }

@Composable
private fun HintCard() {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "TRY SAYING",
                color = VoxColors.Ink,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall,
            )
            listOf(
                "\u201CMove 50 from Groceries to Travel.\u201D",
                "\u201CSet aside 15% of every salary deposit into Emergency.\u201D",
                "\u201CFreeze my entertainment card if I spend more than 50 at bars this week.\u201D",
                "\u201CIf anyone ever charges my main card more than 300 in one go, freeze it.\u201D",
            ).forEach { hint ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(VoxColors.Panel2, RoundedCornerShape(10.dp))
                        .border(1.dp, VoxColors.Line, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
                    Text(hint, color = VoxColors.Ink, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") Spacer(Modifier.width(0.dp))
}

/** Tiny polling helper. Returns a holder with .value and .refresh(). */
class PollState<T>(initial: T?) {
    var value by mutableStateOf<T?>(initial)
    var lastErr by mutableStateOf<Throwable?>(null)
    private val triggers = androidx.compose.runtime.mutableStateOf(0)

    fun refresh() { triggers.value++ }
    val triggerKey: Int get() = triggers.value
}

@Composable
fun <T> pollState(intervalMs: Long, fetch: suspend () -> T): PollState<T> {
    val holder = remember { PollState<T>(null) }
    LaunchedEffect(holder.triggerKey) {
        while (isActive) {
            runCatching { fetch() }
                .onSuccess { holder.value = it; holder.lastErr = null }
                .onFailure { holder.lastErr = it }
            delay(intervalMs)
        }
    }
    return holder
}
