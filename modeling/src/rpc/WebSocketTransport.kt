package rpc

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.rpc.krpc.KrpcTransport
import kotlinx.rpc.krpc.KrpcTransportMessage
import kotlinx.rpc.krpc.KrpcTransportMessage.BinaryMessage
import kotlinx.rpc.krpc.KrpcTransportMessage.StringMessage

fun WebSocketSession.asKrpcTransport(): KrpcTransport {
    val session = this
    return object : KrpcTransport {
        override val coroutineContext = session.coroutineContext

        override suspend fun send(message: KrpcTransportMessage) {
            session.send(
                when (message) {
                    is StringMessage -> Frame.Text(message.value)
                    is BinaryMessage -> Frame.Binary(true, message.value)
                }
            )
        }

        override suspend fun receive(): KrpcTransportMessage {
            while (true) {
                when (val frame = session.incoming.receive()) {
                    is Frame.Text -> return StringMessage(frame.readText())
                    is Frame.Binary -> return BinaryMessage(frame.readBytes())
                    else -> Unit
                }
            }
        }
    }
}
