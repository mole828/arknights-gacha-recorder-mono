package protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AgentHgToken(val content: String)

@Serializable
data class AgentAccountInfo(
    val channelMasterId: Int,
    val channelName: String,
    val isDefault: Boolean,
    val isDeleted: Boolean,
    val isOfficial: Boolean,
    val nickName: String,
    val uid: String,
)

@Serializable
data class AgentGachaInfo(
    val charId: String,
    val charName: String,
    val gachaTs: ULong,
    val isNew: Boolean,
    val poolId: String,
    val poolName: String,
    val pos: UInt,
    val rarity: UInt,
)

@Serializable
sealed interface MessageTemplate {
    @Serializable
    @SerialName("msg")
    data class Msg(val msg: String) : MessageTemplate

    @Serializable
    @SerialName("auth")
    data class Auth(val agentKey: String) : MessageTemplate

    @Serializable
    @SerialName("task")
    data class Task(val hgToken: AgentHgToken, val uid: String?) : MessageTemplate

    @Serializable
    @SerialName("task_result")
    data class TaskResult(
        val result: List<AgentGachaInfo>,
        val uid: String,
        val hgToken: AgentHgToken,
    ) : MessageTemplate

    @Serializable
    @SerialName("expired")
    data class Expired(val hgToken: AgentHgToken) : MessageTemplate

    @Serializable
    @SerialName("token_valid")
    data class TokenValid(val uid: String, val hgToken: AgentHgToken) : MessageTemplate

    @Serializable
    @SerialName("token_invalid")
    data class TokenInvalid(val hgToken: AgentHgToken, val msg: String) : MessageTemplate

    @Serializable
    @SerialName("user_info")
    data class UserInfo(val info: AgentAccountInfo, val hgToken: AgentHgToken) : MessageTemplate
}
