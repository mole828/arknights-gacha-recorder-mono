package com.example

import client.ArkNights
import client.ArkNightsCollector
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
import rpc.Collector
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
private val collector = ArkNightsCollector(ArkNights(apiHttpClient))

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

        server.registerService<Collector> { collector }
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
