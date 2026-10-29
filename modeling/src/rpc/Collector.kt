package rpc

import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable

@Rpc
interface Collector {
    suspend fun validate(token: String): CollectorResult
    suspend fun collect(token: String): CollectorResult
}

@Serializable
sealed interface CollectorResult {
    @Serializable
    data class Success(
        val account: AgentAccountInfo,
        val gachas: List<AgentGachaInfo> = emptyList(),
    ) : CollectorResult

    @Serializable
    data object Expired : CollectorResult

    @Serializable
    data class Invalid(val message: String) : CollectorResult
}

typealias AgentAccountInfo = model.AccountInfo

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
