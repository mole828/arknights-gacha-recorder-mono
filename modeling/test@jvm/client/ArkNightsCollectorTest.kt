package client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import rpc.CollectorResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration

class ArkNightsCollectorTest {
    @Test
    fun collectFollowsHistoryPagesAcrossAllPools() = runBlocking<Unit> {
        val historyCursors = mutableListOf<Pair<String?, String?>>()
        val result = withCollector({ request ->
            when (request.url.encodedPath) {
                "/user/api/inquiry/gacha/cate" ->
                    json("""{"code":0,"msg":"ok","data":[{"id":"a","name":"A"},{"id":"b","name":"B"}]}""")
                "/user/api/inquiry/gacha/history" -> {
                    val pool = request.url.parameters["category"]
                    val ts = request.url.parameters["gachaTs"]
                    historyCursors += ts to request.url.parameters["pos"]
                    when {
                        pool == "a" && ts == null -> json(historyPage(hasMore = true, gacha("a1", ts = 2uL, pos = 1u)))
                        pool == "a" -> json(historyPage(hasMore = false, gacha("a2", ts = 1uL, pos = 0u)))
                        else -> json(historyPage(hasMore = false, gacha("b1", ts = 3uL, pos = 0u)))
                    }
                }
                else -> accountFlow(request)
            }
        }) { it.collect("hg") }

        val success = assertIs<CollectorResult.Success>(result)
        assertEquals("uid", success.account.uid)
        assertEquals(listOf("a1", "a2", "b1"), success.gachas.map { it.charId })
        assertEquals(listOf(null to null, "2" to "1", null to null), historyCursors)
    }

    @Test
    fun validateAndCollectClassifyTokenErrors() = runBlocking<Unit> {
        for ((grantBody, expected) in listOf(
            """{"status":3,"msg":"expired"}""" to CollectorResult.Expired,
            "Error" to CollectorResult.Invalid("HgToken 无效"),
        )) {
            for (call in listOf<suspend (ArkNightsCollector) -> CollectorResult>({ it.validate("hg") }, { it.collect("hg") })) {
                assertEquals(expected, withCollector({ respond(grantBody) }, call))
            }
        }
    }

    @Test
    fun accountWithoutBindingIsInvalid() = runBlocking<Unit> {
        val result = withCollector({ request ->
            if (request.url.encodedPath == "/account/binding/v1/binding_list") {
                json("""{"status":0,"msg":"ok","data":{"list":[{"appCode":"arknights","appName":"明日方舟","bindingList":[]}]}}""")
            } else {
                accountFlow(request)
            }
        }) { it.validate("hg") }
        assertEquals(CollectorResult.Invalid("没有找到绑定账号"), result)
    }

    private suspend fun withCollector(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
        block: suspend (ArkNightsCollector) -> CollectorResult,
    ): CollectorResult {
        val http = HttpClient(MockEngine { handler(it) })
        return try {
            block(ArkNightsCollector(ArkNights(http), pageInterval = Duration.ZERO))
        } finally {
            http.close()
        }
    }

    private fun MockRequestHandleScope.accountFlow(request: HttpRequestData) = when (request.url.encodedPath) {
        "/user/oauth2/v2/grant" ->
            json("""{"status":0,"msg":"ok","type":"","data":{"hgId":"id","token":"app"}}""")
        "/account/binding/v1/binding_list" ->
            json("""{"status":0,"msg":"ok","data":{"list":[{"appCode":"arknights","appName":"明日方舟","bindingList":[{"channelMasterId":1,"channelName":"官服","isDefault":true,"isDeleted":false,"isOfficial":true,"nickName":"Doctor","uid":"uid"}]}]}}""")
        "/account/binding/v1/u8_token_by_uid" -> json("""{"status":0,"msg":"ok","data":{"token":"u8"}}""")
        "/user/api/role/login" -> respond("{}", headers = headersOf(HttpHeaders.SetCookie, "ak-user-center=cookie"))
        else -> error("unexpected path: ${request.url.encodedPath}")
    }

    private fun MockRequestHandleScope.json(body: String) =
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private fun historyPage(hasMore: Boolean, vararg gachas: String) =
        """{"code":0,"msg":"ok","data":{"hasMore":$hasMore,"list":[${gachas.joinToString(",")}]}}"""

    private fun gacha(charId: String, ts: ULong, pos: UInt) =
        """{"charId":"$charId","charName":"Op","gachaTs":$ts,"isNew":false,"poolId":"p","poolName":"P","pos":$pos,"rarity":3}"""
}
