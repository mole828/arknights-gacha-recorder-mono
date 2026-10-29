package com.example.controller

import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.classic.error
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.GachaService
import model.RegisterRequest
import model.RegisterResponse
import model.parseRegistrationToken
import rpc.Collector

private val registrationLogger = Logger.of("Registration")

fun Routing.userPart(service: GachaService, agents: Agents) {
    get("/users") { call.respond(service.users()) }
    get("/users.invalid") { call.respond(service.expiredUsers().map { it.nickName }) }
    get("/gachas") {
        val page = call.queryParameters["page"]?.toLong() ?: 0
        val response = service.gachaPage(call.queryParameters["uid"], page, pageSize = 10)
        call.respond(response)
    }

    post("/register") {
        val token = parseRegistrationToken(call.receive<RegisterRequest>().token)
        val agent = agents.available()
        // CIO sends the status and headers before running this body producer.
        call.respondBytesWriter(ContentType.Application.Json, HttpStatusCode.OK) {
            val response = try {
                register(token, agent, service, call.application)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                registrationLogger.error("记录账号失败", e)
                RegisterResponse("记录失败，请稍后重试")
            }
            writeStringUtf8(Json.encodeToString(response))
        }
    }
}

private suspend fun register(
    token: String,
    agent: Collector?,
    service: GachaService,
    scope: CoroutineScope,
): RegisterResponse {
    val response = service.register(token, agent)
    if (response.uid != null && agent != null) {
        scope.launch {
            try {
                service.collect(agent, token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                registrationLogger.error("后台采集失败", e)
            }
        }
    }
    return response
}
