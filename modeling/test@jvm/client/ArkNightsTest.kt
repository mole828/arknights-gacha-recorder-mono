package client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.HgToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArkNightsTest {
    @Test
    fun accountAndHistoryRequestsKeepTheirWireFormat() = runBlocking<Unit> {
        val paths = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            val body = when (request.url.encodedPath) {
                "/user/info/v1/basic" -> {
                    assertEquals("hg", request.url.parameters["token"])
                    """{"status":0,"msg":"ok","extra":true}"""
                }
                "/user/oauth2/v2/grant" -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("application/json", request.body.contentType.toString())
                    val payload = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals("hg", payload.getValue("token").jsonPrimitive.content)
                    assertEquals("be36d44aa36bfb5b", payload.getValue("appCode").jsonPrimitive.content)
                    assertEquals("1", payload.getValue("type").jsonPrimitive.content)
                    """{"status":0,"msg":"ok","type":"","data":{"hgId":"id","token":"app"}}"""
                }
                "/account/binding/v1/binding_list" -> {
                    assertEquals("app", request.url.parameters["token"])
                    assertEquals("arknights", request.url.parameters["appCode"])
                    """{"status":0,"msg":"ok","data":{"list":[{"appCode":"arknights","appName":"明日方舟","bindingList":[{"channelMasterId":1,"channelName":"官服","isDefault":true,"isDeleted":false,"isOfficial":true,"nickName":"Doctor","uid":"uid"}]}]}}"""
                }
                "/account/binding/v1/u8_token_by_uid" -> {
                    val payload = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals("uid", payload.getValue("uid").jsonPrimitive.content)
                    assertEquals("app", payload.getValue("token").jsonPrimitive.content)
                    """{"status":0,"msg":"ok","data":{"token":"u8"}}"""
                }
                "/user/api/role/login" -> {
                    val payload = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals("u8", payload.getValue("token").jsonPrimitive.content)
                    assertEquals("", payload.getValue("source_from").jsonPrimitive.content)
                    return@MockEngine respond("{}", headers = headersOf(
                        HttpHeaders.SetCookie to listOf("other=ignored", "ak-user-center=cookie; Path=/; HttpOnly"),
                    ))
                }
                "/user/api/inquiry/gacha/cate" -> {
                    assertEquals("uid", request.url.parameters["uid"])
                    assertEquals("u8", request.headers["X-Role-Token"])
                    assertEquals("ak-user-center=cookie", request.headers[HttpHeaders.Cookie])
                    """{"code":0,"msg":"ok","data":[{"id":"pool","name":"Banner"}]}"""
                }
                "/user/api/inquiry/gacha/history" -> {
                    assertEquals("pool", request.url.parameters["category"])
                    assertEquals("10", request.url.parameters["size"])
                    assertEquals(ULong.MAX_VALUE.toString(), request.url.parameters["gachaTs"])
                    assertEquals("9", request.url.parameters["pos"])
                    assertEquals("u8", request.headers["X-Role-Token"])
                    assertEquals("ak-user-center=cookie", request.headers[HttpHeaders.Cookie])
                    """{"code":0,"msg":"ok","data":{"hasMore":false,"list":[{"charId":"char","charName":"Operator","gachaTs":18446744073709551615,"isNew":true,"poolId":"pool","poolName":"Banner","pos":9,"rarity":5}]}}"""
                }
                else -> error("unexpected path: ${request.url.encodedPath}")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val api = ArkNights(http)
            assertTrue(api.checkToken(HgToken("hg")))
            val appToken = api.grantAppToken(HgToken("hg"))
            val account = api.bindingList(appToken).list.single().bindingList.single()
            val u8Token = api.u8TokenByUid(appToken, account.uid)
            val cookie = api.login(u8Token)
            val pool = api.poolList(account.uid, u8Token, cookie).single()
            val history = api.history(cookie, u8Token, pool, 10u, ULong.MAX_VALUE, 9u)
            assertEquals(ULong.MAX_VALUE, history.list.single().gachaTs)
            assertFalse(history.hasMore)
            assertEquals(7, paths.size)
        } finally {
            http.close()
        }
    }

    @Test
    fun expiredAndInvalidTokensRemainDistinct() = runBlocking<Unit> {
        for ((body, expired) in listOf(
            """{"status":3,"msg":"expired"}""" to true,
            "Error" to false,
        )) {
            val http = HttpClient(MockEngine { respond(body) })
            try {
                val api = ArkNights(http)
                if (expired) {
                    assertFailsWith<ArkNights.HgTokenExpired> { api.grantAppToken(HgToken("hg")) }
                } else {
                    assertFailsWith<ArkNights.HgTokenInvalid> { api.grantAppToken(HgToken("hg")) }
                }
            } finally {
                http.close()
            }
        }
    }

    @Test
    fun rejectsFailedCheckAndMissingLoginCookie() = runBlocking<Unit> {
        val http = HttpClient(MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable) })
        try {
            val api = ArkNights(http)
            assertFailsWith<IllegalStateException> { api.checkToken(HgToken("hg")) }
            assertFailsWith<IllegalArgumentException> { api.login(ArkNights.U8Token("u8")) }
        } finally {
            http.close()
        }
    }
}
