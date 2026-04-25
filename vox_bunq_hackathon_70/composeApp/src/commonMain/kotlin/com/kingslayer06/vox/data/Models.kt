package com.kingslayer06.vox.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed class Action {
    abstract val note: String?

    @Serializable
    @kotlinx.serialization.SerialName("transfer")
    data class Transfer(
        val from_account: String,
        val to_account: String,
        val amount_eur: Double,
        override val note: String? = null,
    ) : Action()

    @Serializable
    @kotlinx.serialization.SerialName("recurring_split")
    data class RecurringSplit(
        val trigger_match: String,
        val percentage: Double,
        val to_account: String,
        override val note: String? = null,
    ) : Action()

    @Serializable
    @kotlinx.serialization.SerialName("conditional_freeze")
    data class ConditionalFreeze(
        val merchant_match: String,
        val window_days: Int,
        val threshold_eur: Double,
        val card_label: String,
        override val note: String? = null,
    ) : Action()

    @Serializable
    @kotlinx.serialization.SerialName("transaction_limit_freeze")
    data class TransactionLimitFreeze(
        val max_tx_eur: Double,
        val card_label: String,
        val from_account: String? = null,
        val merchant_match: String? = null,
        override val note: String? = null,
    ) : Action()
}

val Action.kind: String
    get() = when (this) {
        is Action.Transfer -> "transfer"
        is Action.RecurringSplit -> "recurring_split"
        is Action.ConditionalFreeze -> "conditional_freeze"
        is Action.TransactionLimitFreeze -> "transaction_limit_freeze"
    }

@Serializable
data class Plan(
    val actions: List<Action> = emptyList(),
    val summary: String = "",
    val warnings: List<String> = emptyList(),
)

@Serializable
data class SubAccount(
    val id: Long,
    val description: String,
    val balance_eur: Double,
    val iban: String? = null,
    val color: String? = null,
)

@Serializable
data class CardInfo(
    val id: Long,
    val label: String,
    val status: String,
    val type: String? = null,
)

@Serializable
data class RuleView(
    val id: Long,
    val kind: String,
    val summary: String,
    val config: JsonObject = JsonObject(emptyMap()),
    val active: Boolean,
    val created_at: String,
    val fired_count: Int = 0,
)

@Serializable
data class ExecutionResult(
    val action_index: Int,
    val kind: String,
    val ok: Boolean,
    val detail: String,
    val bunq_payment_id: Long? = null,
    val rule_id: Long? = null,
)

@Serializable
data class ExecuteResponse(
    val results: List<ExecutionResult> = emptyList(),
)

@Serializable
data class ExecuteRequest(
    val plan: Plan,
    val selected_indexes: List<Int>? = null,
)

@Serializable
data class PlanRequest(val text: String)

@Serializable
data class FiringEvent(
    val rule_id: Long,
    val rule_kind: String,
    val summary: String,
    val detail: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class Health(
    val ok: Boolean = false,
    val bunq_authenticated: Boolean = false,
    val bunq_error: String? = null,
    val llm_provider: String? = null,
    val transcribe_provider: String? = null,
    val transcribe_mode: String = "browser",
)

@Serializable
data class DemoFireRequest(val amount_eur: Double)
