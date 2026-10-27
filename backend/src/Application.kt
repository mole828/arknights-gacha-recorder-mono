package com.example

import com.example.service.GachaRecorder
import com.example.service.createGachaRepository
import io.ktor.server.application.ServerReady
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

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
    val service = GachaRecorder(createGachaRepository(
        url = requireNotNull(env("DATABASE_URL")),
        username = requireNotNull(env("DATABASE_USER")),
        password = requireNotNull(env("DATABASE_PASSWORD")),
    ))
    runBlocking { service.init() }
    if (env("OPEN_LOOP").toBoolean()) {
        monitor.subscribe(ServerReady) { service.scope.launch { service.mainLoop() } }
    }
    configureRouting(service, requireNotNull(env("AGENT_KEY")))
}
