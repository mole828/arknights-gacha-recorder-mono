package com.example.controller

import api.ArkNights
import com.example.service.GachaRecorder
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.classic.error
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.websocket.close
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.rpc.krpc.client.InitializedKrpcClient
import kotlinx.rpc.krpc.rpcClientConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import rpc.AgentAccountInfo
import rpc.Collector
import rpc.CollectorResult
import rpc.asKrpcTransport
import kotlin.time.Duration.Companion.minutes

class Agents(private val recorder: GachaRecorder) {
    private val connected = MutableStateFlow<Set<Collector>>(emptySet())
    private val pending = ArrayDeque<String>()
    private val queue = Mutex()
    private val log = Logger.of("Agents")

    val count: Int get() = connected.value.size
    fun available(): Collector? = connected.value.randomOrNull()

    suspend fun run(agent: Collector) {
        connected.update { it + agent }
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    val token = queue.withLock {
                        if (pending.isEmpty()) pending.addAll(recorder.activeTasks().shuffled().map { it.hgToken.content })
                        pending.removeFirstOrNull()
                    }
                    if (token != null) collect(agent, token)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("采集失败", e)
                }
                delay(2.minutes)
            }
        } finally {
            connected.update { it - agent }
        }
    }

    suspend fun collect(agent: Collector, token: String): CollectorResult {
        val hgToken = ArkNights.HgToken(token)
        val result = agent.collect(token)
        when (result) {
            is CollectorResult.Success -> {
                recorder.upsert(result.account.toApi(), hgToken)
                recorder.record(result.gachas.map {
                    ArkNights.GachaApi.Gacha.from(result.account.uid, ArkNights.GachaApi.GachaInfo.Companion.DefaultImpl(
                        charId = it.charId, charName = it.charName, gachaTs = it.gachaTs,
                        isNew = it.isNew, poolId = it.poolId, poolName = it.poolName,
                        pos = it.pos, rarity = it.rarity,
                    ))
                })
                recorder.setTokenExpired(hgToken, false)
            }
            CollectorResult.Expired, is CollectorResult.Invalid -> recorder.setTokenExpired(hgToken, true)
        }
        return result
    }
}

internal fun AgentAccountInfo.toApi() = ArkNights.AccountInfo(
    channelMasterId = channelMasterId,
    channelName = channelName,
    isDefault = isDefault,
    isDeleted = isDeleted,
    isOfficial = isOfficial,
    nickName = nickName,
    uid = uid,
)

fun Routing.agentPart(agentKey: String, agents: Agents) {
    get("/agent/status") { call.respond(mapOf("connected" to agents.count)) }

    webSocket("/agent/ws") {
        if (call.request.headers[HttpHeaders.Authorization] != "Bearer $agentKey") {
            close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            return@webSocket
        }
        val client = object : InitializedKrpcClient(
            rpcClientConfig { serialization { json() } }, asKrpcTransport(),
        ) {}
        try {
            coroutineScope {
                val worker = launch { agents.run(client.withService<Collector>()) }
                try { closeReason.await() } finally { worker.cancelAndJoin() }
            }
        } finally {
            client.close()
        }
    }
}
