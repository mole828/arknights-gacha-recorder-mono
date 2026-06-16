package com.example

import com.example.controller.agentPart
import com.example.controller.userPart
import com.example.service.GachaRecorder
import com.example.service.createGachaRepository
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.classic.info
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ServerReady
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

fun Application.configureRouting() {
    val log = Logger.of("Application")

    @Serializable
    class ResponseTemplate(
        val code: Int,
        val msg: String,
    )

    install(StatusPages) {
        exception<IllegalArgumentException> { call: ApplicationCall, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ResponseTemplate(code = -1, msg = cause.message ?: "parameter does not match")
            )
        }
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val dbUrl = requireNotNull(env("DATABASE_URL")) { "DATABASE_URL is not set" }
    val dbUser = requireNotNull(env("DATABASE_USER")) { "DATABASE_USER is not set" }
    val dbPassword = requireNotNull(env("DATABASE_PASSWORD")) { "DATABASE_PASSWORD is not set" }

    val service = GachaRecorder(
        repository = createGachaRepository(
            url = dbUrl,
            username = dbUser,
            password = dbPassword,
        )
    )
    runBlocking {
        service.init()
    }

    val openLoop = env("OPEN_LOOP")?.toBoolean() ?: false
    log.info("OPEN_LOOP: $openLoop")
    if (openLoop) {
        monitor.subscribe(ServerReady) {
            service.scope.launch {
                service.mainLoop()
            }
        }
    }

    val agentKey = requireNotNull(env("AGENT_KEY")) { "AGENT_KEY is not set" }

    routing {
        @Serializable
        data class HealthCheckStruct(
            val msg: String,
            val mainLoopRunning: Map<String, Boolean>,
        )

        get("/healthcheck") {
            call.respondText(
                Json.encodeToString(
                    HealthCheckStruct(
                        msg = "ok",
                        mainLoopRunning = service.mainLoopRunning(),
                    )
                ),
                ContentType.Application.Json
            )
        }

        val (agentList, taskList) = agentPart(agentKey, service)
        userPart(service = service, agentList = agentList, taskList = taskList)

        webSocket("/echo") {
            for (frame in incoming) {
                frame as? Frame.Text ?: continue
                send(frame)
            }
        }
    }
}
