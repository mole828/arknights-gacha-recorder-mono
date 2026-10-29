package com.example

import client.ArkNights
import model.AccountInfo
import model.HgToken
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.registerService
import kotlinx.rpc.krpc.rpcServerConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.krpc.server.KrpcServer
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
private val serverUrl = System.getenv("SERVER_URL") ?: "ws://$serverHost:$serverPort$serverPath"

private val ktorClient = HttpClient(CIO) {
    install(WebSockets) {
        pingIntervalMillis = 60_000
    }
}

private val apiHttpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 10_000
        socketTimeoutMillis = 5_000
    }
}
private val api = ArkNights(apiHttpClient)

private data class AccountContext(
    val appToken: ArkNights.AppToken,
    val account: AccountInfo,
)

private suspend fun resolveAccount(token: String): AccountContext {
    val hgToken = HgToken(token)
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
        CollectorResult.Success(account = account)
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
        val pools = api.poolList(uid, u8Token, loginCookie)
        val gachas = mutableListOf<AgentGachaInfo>()

        for (pool in pools) {
            var history = api.history(
                loginCookie = loginCookie,
                u8Token = u8Token,
                pool = pool,
                size = 10u,
            )
            gachas.addAll(history.list)

            while (history.hasMore) {
                val lastGacha = history.list.lastOrNull() ?: break
                history = api.history(
                    loginCookie = loginCookie,
                    u8Token = u8Token,
                    pool = pool,
                    size = 10u,
                    gachaTs = lastGacha.gachaTs,
                    pos = lastGacha.pos,
                )
                gachas.addAll(history.list)
            }
        }

        CollectorResult.Success(
            account = accountContext.account,
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
        urlString = serverUrl,
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
        println("kRPC collector connected")

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
        apiHttpClient.close()
    }
}
