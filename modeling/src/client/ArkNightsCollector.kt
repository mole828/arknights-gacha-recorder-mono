package client

import kotlinx.coroutines.delay
import model.AccountInfo
import model.HgToken
import rpc.AgentGachaInfo
import rpc.Collector
import rpc.CollectorResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The single Hypergryph collection flow, shared by the standalone agent and the backend's local mode.
 *
 * [pageInterval] is waited between consecutive history pages to avoid hammering the official API.
 */
class ArkNightsCollector(
    private val api: ArkNights,
    private val pageSize: UInt = 10u,
    private val pageInterval: Duration = 5.seconds,
) : Collector {
    override suspend fun validate(token: String): CollectorResult = classifyTokenErrors {
        CollectorResult.Success(account = resolveAccount(HgToken(token)).account)
    }

    override suspend fun collect(token: String): CollectorResult = classifyTokenErrors {
        val (appToken, account) = resolveAccount(HgToken(token))
        val u8Token = api.u8TokenByUid(appToken, account.uid)
        val loginCookie = api.login(u8Token)
        val gachas = api.poolList(account.uid, u8Token, loginCookie).flatMap { pool ->
            poolHistory(loginCookie, u8Token, pool)
        }
        CollectorResult.Success(account = account, gachas = gachas)
    }

    private suspend fun poolHistory(
        loginCookie: ArkNights.LoginCookie,
        u8Token: ArkNights.U8Token,
        pool: ArkNights.Pool,
    ): List<AgentGachaInfo> = buildList {
        var page = api.history(loginCookie, u8Token, pool, pageSize)
        while (true) {
            addAll(page.list)
            val last = page.list.lastOrNull()
            if (!page.hasMore || last == null) break

            delay(pageInterval)
            page = api.history(loginCookie, u8Token, pool, pageSize, gachaTs = last.gachaTs, pos = last.pos)
        }
    }

    private data class AccountContext(val appToken: ArkNights.AppToken, val account: AccountInfo)

    private suspend fun resolveAccount(hgToken: HgToken): AccountContext {
        val appToken = api.grantAppToken(hgToken)
        val account = api.bindingList(appToken).list
            .firstNotNullOfOrNull { it.bindingList.firstOrNull() }
            ?: throw ArkNights.HgTokenInvalid(hgToken, "没有找到绑定账号")
        return AccountContext(appToken, account)
    }

    private inline fun classifyTokenErrors(block: () -> CollectorResult): CollectorResult = try {
        block()
    } catch (_: ArkNights.HgTokenExpired) {
        CollectorResult.Expired
    } catch (e: ArkNights.HgTokenInvalid) {
        CollectorResult.Invalid(e.msg)
    }
}
