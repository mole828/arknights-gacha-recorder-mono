package com.example.controller

import agent.Agent
import api.ArkNights
import com.example.service.GachaRecorder
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.classic.info
import io.ktor.http.ContentType
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import protocol.AgentHgToken
import protocol.MessageTemplate
import kotlin.time.Duration.Companion.seconds

fun Routing.userPart(service: GachaRecorder, taskList: MutableList<MessageTemplate.Task>, agentList: List<Agent>) {
    val log = Logger.of("UserRoute")

    get("/users") {
        call.respond(service.users())
    }

    get("/users.invalid") {
        call.respond(service.expiredUsers().map { it.nickName })
    }

    val pageSize = 10
    get("/gachas") {
        val page: Long = call.queryParameters["page"]?.toLong() ?: 0
        val uid: String? = call.queryParameters["uid"]
        val resp = service.gachaPage(uid = uid, page = page, pageSize = pageSize)
        call.respondText(Json.encodeToString(resp), ContentType.Application.Json)
    }

    post("/register") {
        val tokenPerhaps = call.queryParameters["token"]
        log.info("register: $tokenPerhaps")
        requireNotNull(tokenPerhaps) { "token is required" }

        val hgToken = try {
            Json.decodeFromString<ArkNights.HgTokenResponse>(tokenPerhaps).data
        } catch (_: Throwable) {
            ArkNights.HgToken(tokenPerhaps)
        }

        val agent = agentList.randomOrNull() ?: run {
            call.respond(mapOf("msg" to "提交成功, 请等待记录员处理"))
            return@post
        }

        val tokenValid = CompletableDeferred<MessageTemplate>()
        val fn: suspend (MessageTemplate) -> Unit = { msg ->
            when (msg) {
                is MessageTemplate.TokenValid -> tokenValid.complete(msg)
                is MessageTemplate.Expired -> if (msg.hgToken.content == tokenPerhaps) tokenValid.complete(msg)
                is MessageTemplate.TokenInvalid -> if (msg.hgToken.content == tokenPerhaps) tokenValid.complete(msg)
                else -> {}
            }
        }

        agent.onMessageActions.add(fn)
        agent.send(MessageTemplate.Task(AgentHgToken(hgToken.content), null))

        val returnData = withTimeoutOrNull(10.seconds) {
            when (tokenValid.await()) {
                is MessageTemplate.TokenValid -> mapOf("msg" to "提交成功")
                is MessageTemplate.TokenInvalid -> mapOf("msg" to "HgToken 无效")
                is MessageTemplate.Expired -> mapOf("msg" to "HgToken 已过期")
                else -> mapOf("msg" to "未知错误")
            }
        }
        agent.onMessageActions.remove(fn)
        call.respond(returnData ?: mapOf("msg" to "未知错误"))
    }
}
