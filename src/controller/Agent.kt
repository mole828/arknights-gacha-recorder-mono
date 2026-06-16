package com.example.controller

import agent.Agent
import agent.MessageTemplate
import agent.WebSocketAgent
import api.ArkNights
import api.Uid
import com.example.service.GachaRecorder
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.classic.info
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

fun Routing.agentPart(
    agentKey: String,
    service: GachaRecorder,
    delayTime: kotlin.time.Duration = 2.minutes
): Pair<MutableList<Agent>, MutableList<MessageTemplate.Task>> {
    val log = Logger.of("AgentRoute")

    log.info("agentKey: $agentKey")
    log.info("delayTime: $delayTime")

    val agentList = mutableListOf<Agent>()
    get("/agent/status") {
        call.respond(agentList)
    }

    val taskList = mutableListOf<MessageTemplate.Task>()
    suspend fun getTask(): MessageTemplate.Task {
        if (taskList.isEmpty()) {
            log.info("reload taskList")
            service.activeTasks().forEach {
                taskList.add(MessageTemplate.Task(uid = it.uid, hgToken = it.hgToken))
            }
            taskList.shuffle()
        }
        return taskList.removeFirst()
    }

    get("/agent/task") {
        val key = call.parameters["agentKey"]
        requireNotNull(key)
        require(agentKey == key)
        call.respond(getTask())
    }

    @Serializable
    data class TaskResult(
        val uid: Uid,
        val hgToken: ArkNights.HgToken,
        val gachas: List<ArkNights.GachaApi.GachaInfo.Companion.DefaultImpl>,
        val expired: Boolean? = false,
    )

    post("/agent/task") {
        val key = call.parameters["agentKey"]
        requireNotNull(key)
        require(agentKey == key)

        val result = Json.decodeFromString<TaskResult>(call.receiveText())
        if (result.expired == true) {
            service.setTokenExpired(result.hgToken, true)
        }

        log.info("agent 更新用户数据, total: ${result.gachas.size}")
        service.record(result.gachas.map {
            ArkNights.GachaApi.Gacha.from(
                uid = result.uid,
                gachaInfo = it,
            )
        })
        call.respond(mapOf("msg" to "ok"))
    }

    webSocket("/agent/ws") {
        val agent = WebSocketAgent(this)
        agent.waitAuth(agentKey)
        agentList.add(agent)

        val fn: suspend (MessageTemplate) -> Unit = { msg ->
            log.info("收到消息: ${msg.toString().take(20)}")
            when (msg) {
                is MessageTemplate.TaskResult -> {
                    log.info("收到任务结果 ${msg.uid}, 共计 ${msg.result.size} 条")
                    launch {
                        log.info("代理人延迟 ${delayTime.inWholeMinutes} 分钟")
                        delay(delayTime)
                        val task = getTask()
                        agent.send(MessageTemplate.Task(hgToken = task.hgToken, uid = task.uid))
                    }
                    async {
                        val hasRecord = service.record(
                            msg.result.map { ArkNights.GachaApi.Gacha.from(uid = msg.uid, gachaInfo = it) }
                        )
                        log.info("完成记录 $hasRecord 条")
                        service.setTokenExpired(msg.hgToken, false)
                        log.info("重制 hgToken 过期状态, 准备开启延迟进程")
                    }.await()
                }

                is MessageTemplate.Expired -> {
                    service.setTokenExpired(msg.hgToken, true)
                }

                is MessageTemplate.UserInfo -> {
                    log.info("更新用户信息: ${msg.info}")
                    service.upsert(msg.info, msg.hgToken)
                }

                else -> {}
            }
        }

        agent.onMessageActions.add(fn)
        log.info("开始派发任务")
        agent.send(getTask().let { MessageTemplate.Task(hgToken = it.hgToken, uid = it.uid) })
        agent.reading.await()
        agentList.remove(agent)
    }

    return agentList to taskList
}
