package com.example

import client.ArkNights
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.HttpTimeout
import com.example.service.GachaRecorder
import com.example.service.createGachaRepository
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    val port = env("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, port = port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    val repository = createGachaRepository(
        url = requireNotNull(env("DATABASE_URL")),
        username = requireNotNull(env("DATABASE_USER")),
        password = requireNotNull(env("DATABASE_PASSWORD")),
    )
    runBlocking { repository.initSchema() }
    val apiHttpClient = HttpClient(ClientCIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 5_000
        }
    }
    val service = GachaRecorder(repository, ArkNights(apiHttpClient))
    monitor.subscribe(ApplicationStopped) {
        service.scope.cancel()
        apiHttpClient.close()
    }
    if (env("OPEN_LOOP").toBoolean()) {
        monitor.subscribe(ServerReady) { service.scope.launch { service.mainLoop() } }
    }
    configureRouting(service, requireNotNull(env("AGENT_KEY")))
}
