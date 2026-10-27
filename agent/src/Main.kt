package com.example

import api.ArkNights
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.registerService
import kotlinx.rpc.krpc.rpcServerConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.krpc.server.KrpcServer
import rpc.AgentAccountInfo
import rpc.AgentGachaInfo
import rpc.Collector
import rpc.CollectorResult
import rpc.asKrpcTransport
import kotlin.time.Duration.Companion.seconds

private val agentKey = requireNotNull(System.getenv("AGENT_KEY")) {
    "AGENT_KEY is not set"
}
private val serverHost = System.getenv("SERVER_HOST") ?: "localhost"
private val serverPort = System.getenv("SERVER_PORT")?.toInt() ?: 8080
private val serverPath = System.getenv("SERVER_PATH") ?: "/agent/ws"

private val ktorClient = HttpClient(CIO) {
    install(WebSockets) {
        pingIntervalMillis = 60_000
    }
}

private val api = ArkNights.default()
private val gachaApi = ArkNights.GachaApi.default()

private data class AccountContext(
    val appToken: ArkNights.AppToken,
    val account: ArkNights.AccountInfo,
)

private fun ArkNights.AccountInfo.toRpc() = AgentAccountInfo(
    channelMasterId = channelMasterId,
    channelName = channelName,
    isDefault = isDefault,
    isDeleted = isDeleted,
    isOfficial = isOfficial,
    nickName = nickName,
    uid = uid.value,
)

private fun ArkNights.GachaApi.GachaInfo.Companion.DefaultImpl.toRpc() = AgentGachaInfo(
    charId = charId,
    charName = charName,
    gachaTs = gachaTs,
    isNew = isNew,
    poolId = poolId,
    poolName = poolName,
    pos = pos,
    rarity = rarity,
)

private suspend fun resolveAccount(token: String): AccountContext {
    val hgToken = ArkNights.HgToken(token)
    val appToken = api.grantAppToken(hgToken)
    val account = api.bindingList(appToken).list
        .asSequence()
        .flatMap { it.bindingList.asSequence() }
        .firstOrNull()
        ?: throw ArkNights.HgTokenInvalid(hgToken, "没有找到绑定账号")

    return AccountContext(appToken, account)
}

private class AgentCollector : Collector {
    override suspend fun validate(token: String): CollectorResult = try {
        val account = resolveAccount(token).account
        CollectorResult.Success(account = account.toRpc())
    } catch (_: ArkNights.HgTokenExpired) {
        CollectorResult.Expired
    } catch (e: ArkNights.HgTokenInvalid) {
        CollectorResult.Invalid(e.msg)
    }

    override suspend fun collect(token: String): CollectorResult = try {
        val accountContext = resolveAccount(token)
        val uid = accountContext.account.uid
        val u8Token = api.u8TokenByUid(accountContext.appToken, uid)
        val loginCookie = api.login(u8Token)
        val pools = gachaApi.poolList(uid, u8Token, loginCookie)
        val gachas = mutableListOf<AgentGachaInfo>()

        for (pool in pools) {
            var history = gachaApi.history(
                loginCookie = loginCookie,
                u8Token = u8Token,
                uid = uid,
                pool = pool,
                size = 10u,
            )
            gachas.addAll(history.list.map { it.toRpc() })

            while (history.hasMore) {
                val lastGacha = history.list.lastOrNull() ?: break
                history = gachaApi.history(
                    loginCookie = loginCookie,
                    u8Token = u8Token,
                    uid = uid,
                    pool = pool,
                    size = 10u,
                    gachaTs = lastGacha.gachaTs,
                    pos = lastGacha.pos,
                )
                gachas.addAll(history.list.map { it.toRpc() })
            }
        }

        CollectorResult.Success(
            account = accountContext.account.toRpc(),
            gachas = gachas,
        )
    } catch (_: ArkNights.HgTokenExpired) {
        CollectorResult.Expired
    } catch (e: ArkNights.HgTokenInvalid) {
        CollectorResult.Invalid(e.msg)
    }
}

private suspend fun connectOnce() {
    ktorClient.webSocket(
        method = HttpMethod.Get,
        host = serverHost,
        port = serverPort,
        path = serverPath,
        request = {
            header(HttpHeaders.Authorization, "Bearer $agentKey")
        },
    ) {
        val server = object : KrpcServer(
            rpcServerConfig {
                serialization { json() }
            },
            asKrpcTransport(),
        ) {}

        server.registerService<Collector> { AgentCollector() }
        println("kRPC collector connected to $serverHost:$serverPort$serverPath")

        try {
            closeReason.await()
        } finally {
            server.close("WebSocket session ended")
        }
    }
}

fun main() = runBlocking {
    try {
        while (true) {
            try {
                connectOnce()
                println("kRPC connection closed")
            } catch (e: CancellationException) {
                ensureActive()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            delay(2.seconds)
        }
    } finally {
        ktorClient.close()
    }
}
