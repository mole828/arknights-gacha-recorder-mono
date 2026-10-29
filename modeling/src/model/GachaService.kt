package model

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import rpc.Collector
import rpc.CollectorResult
import kotlin.time.Duration.Companion.seconds

class GachaService(private val repository: GachaRepository) {
    suspend fun users(): List<AccountInfo> = repository.users()
    suspend fun expiredUsers(): List<AccountInfo> = repository.expiredUsers()
    suspend fun activeTasks(): List<UserToken> = repository.activeTokens()

    suspend fun gachaPage(uid: Uid?, page: Long, pageSize: Int): GachasResponse {
        val request = GachaPageRequest(page, pageSize)
        return repository.gachaPage(uid, request.page, request.pageSize)
    }

    suspend fun register(token: String, collector: Collector?): RegisterResponse {
        if (collector == null) return RegisterResponse("记录员未连接，请稍后重试")
        return when (val result = withTimeoutOrNull(10.seconds) { collector.validate(token) }) {
            is CollectorResult.Success -> {
                repository.upsertUser(result.account, HgToken(token))
                RegisterResponse("记录成功，后续会陆续补全", result.account.uid)
            }
            CollectorResult.Expired -> {
                repository.setTokenExpired(HgToken(token), true)
                RegisterResponse("HgToken 已过期")
            }
            is CollectorResult.Invalid -> RegisterResponse("HgToken 无效")
            null -> RegisterResponse("验证超时，请重试")
        }
    }

    suspend fun collect(collector: Collector, token: String): CollectorResult {
        val result = collector.collect(token)
        val hgToken = HgToken(token)
        when (result) {
            is CollectorResult.Success -> {
                repository.upsertUser(result.account, hgToken)
                record(result.gachas.map { it.toRecord(result.account.uid) })
                repository.setTokenExpired(hgToken, false)
            }
            CollectorResult.Expired, is CollectorResult.Invalid -> repository.setTokenExpired(hgToken, true)
        }
        return result
    }

    suspend fun record(records: List<GachaRecord>): UInt =
        records.sumOf { repository.record(it) }
}

/** Accept either a token or the complete JSON copied from the account page. */
fun parseRegistrationToken(input: String): String {
    val trimmed = input.trim()
    require(trimmed.isNotEmpty()) { "token is required" }
    val token = try {
        Json.decodeFromString<TokenResponse>(trimmed).data.content
    } catch (_: IllegalArgumentException) {
        trimmed
    }
    require(token.isNotBlank()) { "token is required" }
    return token
}

@Serializable
private data class TokenResponse(val code: Int, val data: HgToken, val msg: String)
