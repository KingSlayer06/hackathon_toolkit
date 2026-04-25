package com.kingslayer06.vox.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class VoxApi(
    private val baseUrl: () -> String = { VoxConfig.baseUrl },
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    val client: HttpClient = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 60_000
        }
    }

    private fun url(path: String): String = "${baseUrl().trimEnd('/')}$path"

    suspend fun health(): Health = client.get(url("/health")).body()
    suspend fun accounts(): List<SubAccount> = client.get(url("/accounts")).body()
    suspend fun cards(): List<CardInfo> = client.get(url("/cards")).body()
    suspend fun rules(): List<RuleView> = client.get(url("/rules")).body()

    suspend fun deleteRule(id: Long): Boolean {
        client.delete(url("/rules/$id"))
        return true
    }

    suspend fun plan(text: String): Plan = client.post(url("/plan")) {
        contentType(ContentType.Application.Json)
        setBody(PlanRequest(text))
    }.body()

    suspend fun execute(plan: Plan, selected: List<Int>?): ExecuteResponse =
        client.post(url("/execute")) {
            contentType(ContentType.Application.Json)
            setBody(ExecuteRequest(plan, selected))
        }.body()

    suspend fun fireSalary(amountEur: Double = 2000.0) = fire("/demo/fire-salary", amountEur)
    suspend fun fireBarSpend(amountEur: Double = 35.0) = fire("/demo/fire-bar-spend", amountEur)
    suspend fun fireLargeTx(amountEur: Double = 500.0) = fire("/demo/fire-large-tx", amountEur)

    private suspend fun fire(path: String, amountEur: Double) {
        client.post(url(path)) {
            contentType(ContentType.Application.Json)
            setBody(DemoFireRequest(amountEur))
        }
    }

    /** SSE — emits a [FiringEvent] each time the backend pushes a `firing` event. */
    fun firingStream(): Flow<FiringEvent> = flow {
        client.prepareGet(url("/events")) {
            // Long-polling-ish — the server holds the connection open.
        }.execute { response ->
            val channel = response.bodyAsChannel()
            var eventName: String? = null
            val data = StringBuilder()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.isEmpty() -> {
                        if (eventName == "firing" && data.isNotEmpty()) {
                            runCatching { json.decodeFromString<FiringEvent>(data.toString()) }
                                .onSuccess { emit(it) }
                        }
                        eventName = null
                        data.clear()
                    }
                    line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data:").trim())
                    }
                    else -> { /* comment line (`:` keepalive) — ignore */ }
                }
            }
        }
    }
}
