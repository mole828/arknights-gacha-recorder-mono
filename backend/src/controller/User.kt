package com.example.controller

import api.ArkNights
import com.example.service.GachaRecorder
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.classic.error
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.RegisterRequest
import model.RegisterResponse
import rpc.Collector
import rpc.CollectorResult
import kotlin.time.Duration.Companion.seconds

private val registrationLogger = Logger.of("Registration")

fun Routing.userPart(service: GachaRecorder, agents: Agents) {
    get("/users") { call.respond(service.users()) }
    get("/users.invalid") { call.respond(service.expiredUsers().map { it.nickName }) }
    get("/gachas") {
        val page = call.queryParameters["page"]?.toLong() ?: 0
        val response = service.gachaPage(call.queryParameters["uid"], page, pageSize = 10)
        call.respondText(Json.encodeToString(response), ContentType.Application.Json)
    }

    post("/register") {
        val input = call.receive<RegisterRequest>().token.trim()
        require(input.isNotEmpty()) { "token is required" }
        val token = try {
            Json.decodeFromString<ArkNights.HgTokenResponse>(input).data.content
        } catch (_: IllegalArgumentException) {
            input
        }
        require(token.isNotBlank()) { "token is required" }
        val agent = agents.available()
        // CIO sends the status and headers before running this body producer.
        call.respondBytesWriter(ContentType.Application.Json, HttpStatusCode.OK) {
            val response = try {
                register(token, agent, service, agents)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                registrationLogger.error("注册采集失败", e)
                RegisterResponse("采集失败，请稍后重试")
            }
            writeStringUtf8(Json.encodeToString(response))
        }
    }
}

private suspend fun register(
    token: String,
    agent: Collector?,
    service: GachaRecorder,
    agents: Agents,
): RegisterResponse {
    if (agent == null) return RegisterResponse("记录员未连接，请稍后重试")

    return when (val result = withTimeoutOrNull(10.seconds) { agent.validate(token) }) {
        is CollectorResult.Success -> {
            service.upsert(result.account.toApi(), ArkNights.HgToken(token))
            when (val collection = agents.collect(agent, token)) {
                is CollectorResult.Success -> RegisterResponse("提交成功", collection.account.uid)
                CollectorResult.Expired -> RegisterResponse("HgToken 已过期")
                is CollectorResult.Invalid -> RegisterResponse("HgToken 无效")
            }
        }
        CollectorResult.Expired -> {
            service.setTokenExpired(ArkNights.HgToken(token), true)
            RegisterResponse("HgToken 已过期")
        }
        is CollectorResult.Invalid -> RegisterResponse("HgToken 无效")
        null -> RegisterResponse("验证超时，请重试")
    }
}
