package com.example

import com.example.controller.Agents
import com.example.controller.agentPart
import com.example.controller.userPart
import com.example.service.GachaRecorder
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

fun Application.configureRouting(service: GachaRecorder, agentKey: String) {
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

    val agents = Agents(service)

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

        agentPart(agentKey, agents)
        userPart(service, agents)
    }
}
