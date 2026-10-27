package com.example.controller

import api.ArkNights
import com.example.service.GachaRecorder
import io.ktor.http.ContentType
import io.ktor.server.application.application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import rpc.CollectorResult
import kotlin.time.Duration.Companion.seconds

fun Routing.userPart(service: GachaRecorder, agents: Agents) {
    get("/users") { call.respond(service.users()) }
    get("/users.invalid") { call.respond(service.expiredUsers().map { it.nickName }) }
    get("/gachas") {
        val page = call.queryParameters["page"]?.toLong() ?: 0
        val response = service.gachaPage(call.queryParameters["uid"], page, pageSize = 10)
        call.respondText(Json.encodeToString(response), ContentType.Application.Json)
    }

    post("/register") {
        val input = requireNotNull(call.queryParameters["token"]) { "token is required" }
        val token = try {
            Json.decodeFromString<ArkNights.HgTokenResponse>(input).data.content
        } catch (_: IllegalArgumentException) {
            input
        }
        val agent = agents.available()
        val message = if (agent == null) {
            "记录员未连接，请稍后重试"
        } else {
            when (val result = withTimeoutOrNull(10.seconds) { agent.validate(token) }) {
                is CollectorResult.Success -> {
                    service.upsert(result.account.toApi(), ArkNights.HgToken(token))
                    call.application.launch { agents.collect(agent, token) }
                    "提交成功"
                }
                CollectorResult.Expired -> {
                    service.setTokenExpired(ArkNights.HgToken(token), true)
                    "HgToken 已过期"
                }
                is CollectorResult.Invalid -> "HgToken 无效"
                null -> "验证超时，请重试"
            }
        }
        call.respond(mapOf("msg" to message))
    }
}
