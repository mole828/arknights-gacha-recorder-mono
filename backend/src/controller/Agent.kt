package com.example.controller

import com.example.service.Agents
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.close
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.rpc.krpc.client.InitializedKrpcClient
import kotlinx.rpc.krpc.rpcClientConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import rpc.Collector
import rpc.asKrpcTransport

fun Routing.agentPart(agentKey: String, agents: Agents) {
    get("/agent/status") { call.respond(mapOf("connected" to agents.count)) }

    webSocket("/agent/ws") {
        if (call.request.headers[HttpHeaders.Authorization] != "Bearer $agentKey") {
            close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            return@webSocket
        }
        val client = object : InitializedKrpcClient(
            rpcClientConfig { serialization { json() } }, asKrpcTransport(),
        ) {}
        try {
            coroutineScope {
                val worker = launch { agents.run(client.withService<Collector>()) }
                try { closeReason.await() } finally { worker.cancelAndJoin() }
            }
        } finally {
            client.close()
        }
    }
}
