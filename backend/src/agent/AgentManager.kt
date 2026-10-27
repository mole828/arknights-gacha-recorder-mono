package agent

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.classic.info
import protocol.MessageTemplate

interface BasePeer {
    suspend fun send(msg: MessageTemplate)
    val onMessageActions: MutableList<suspend (MessageTemplate) -> Unit>
    val scope: CoroutineScope
    suspend fun onMessage (msg: MessageTemplate): Unit {
        onMessageActions.forEach { scope.async{ it(msg) } }
    }
}

interface Agent : BasePeer {
    // for server use
    suspend fun waitAuth(agentKey: String) {

        var authed = CompletableDeferred<Boolean>()
        val fn: suspend (MessageTemplate) -> Unit = { msg ->
            if (msg is MessageTemplate.Auth) {
                if (msg.agentKey == agentKey) {
                    send(MessageTemplate.Msg("auth success"))
                    authed.complete(true)
                } else {
                    send(MessageTemplate.Msg("auth fail"))
                }
            } else {
                send(MessageTemplate.Msg("not auth"))
            }
        }
        try {
            this.onMessageActions.add(fn)
            if(authed.await().not()) {
                throw Exception("auth fail")
            }
        } catch (e: Throwable) {
            throw e
        } finally {
            this.onMessageActions.remove(fn)
        }
    }
}
interface Server : BasePeer

class WebSocketAgent(val session: WebSocketSession) : Agent {
    companion object {
        val json = Json
        private val scope = CoroutineScope(Dispatchers.Default)
        val logger = Logger.of(WebSocketAgent::class)
    }
    override val scope = Companion.scope
    override suspend fun send(msg: MessageTemplate) {
        val msgStr = json.encodeToString(msg)
        session.send(Frame.Text(msgStr))
    }
    override val onMessageActions = mutableListOf<suspend (MessageTemplate) -> Unit>()
    val reading = scope.async {
        logger.info("agent start reading")
        try {
            for (frame in session.incoming) {
                frame as? Frame.Text ?: continue
                val receivedText = frame.readText()
                val msg = json.decodeFromString<MessageTemplate>(receivedText)
                onMessage(msg)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        logger.info("agent end reading")
    }
}
