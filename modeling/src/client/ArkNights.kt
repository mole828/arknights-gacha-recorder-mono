package client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.setCookie
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.AccountInfo
import model.HgToken
import model.Uid
import rpc.AgentGachaInfo

/** The caller owns the HTTP client and its engine, timeouts and lifecycle. */
class ArkNights(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkToken(hgToken: HgToken): Boolean {
        val response = http.get("https://as.hypergryph.com/user/info/v1/basic") {
            parameter("token", hgToken.content)
        }
        check(response.status == HttpStatusCode.OK) { "checkToken 失败, status: ${response.status}" }
        return json.decodeFromString<BaseResponse>(response.bodyAsText()).status == 0
    }

    suspend fun grantAppToken(hgToken: HgToken): AppToken {
        val response = http.post("https://as.hypergryph.com/user/oauth2/v2/grant") {
            headers { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) }
            setBody(json.encodeToString(GrantAppTokenPayload("be36d44aa36bfb5b", hgToken.content, 1)))
        }
        val body = response.bodyAsText()
        if ("Error" in body) throw HgTokenInvalid(hgToken)
        if (json.decodeFromString<BaseResponse>(body).status == 3) throw HgTokenExpired(hgToken)
        return json.decodeFromString<AppTokenResponse>(body).data
    }

    suspend fun bindingList(appToken: AppToken): MultiAppBindingList {
        val response = http.get("https://binding-api-account-prod.hypergryph.com/account/binding/v1/binding_list") {
            headers { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) }
            parameter("token", appToken.token)
            parameter("appCode", "arknights")
        }
        return json.decodeFromString<BindingListResponse>(response.bodyAsText()).data
    }

    suspend fun u8TokenByUid(appToken: AppToken, uid: Uid): U8Token {
        val response = http.post("https://binding-api-account-prod.hypergryph.com/account/binding/v1/u8_token_by_uid") {
            headers { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) }
            setBody(json.encodeToString(mapOf("token" to appToken.token, "uid" to uid)))
        }
        val result = json.decodeFromString<U8TokenResponse>(response.bodyAsText())
        check(result.status == 0) { "u8TokenByUid 失败, status: ${result.status}, msg: ${result.msg}" }
        return requireNotNull(result.data)
    }

    suspend fun login(u8Token: U8Token): LoginCookie {
        val response = http.post("https://ak.hypergryph.com/user/api/role/login") {
            headers { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) }
            setBody(json.encodeToString(mapOf(
                "token" to u8Token.token,
                "source_from" to "", "share_type" to "", "share_by" to "",
            )))
        }
        val cookie = requireNotNull(response.setCookie().firstOrNull { it.name == "ak-user-center" })
        return LoginCookie(cookie.value)
    }

    suspend fun poolList(uid: Uid, u8Token: U8Token, loginCookie: LoginCookie): List<Pool> {
        val response = http.get("https://ak.hypergryph.com/user/api/inquiry/gacha/cate") {
            parameter("uid", uid)
            headers {
                append("X-Role-Token", u8Token.token)
                append(HttpHeaders.Cookie, "ak-user-center=${loginCookie.content}")
            }
        }
        return json.decodeFromString<PoolListResponse>(response.bodyAsText()).data
    }

    suspend fun history(
        loginCookie: LoginCookie,
        u8Token: U8Token,
        pool: Pool,
        size: UInt,
        gachaTs: ULong? = null,
        pos: UInt? = null,
    ): GachaListData {
        val response = http.get("https://ak.hypergryph.com/user/api/inquiry/gacha/history") {
            parameter("category", pool.id)
            parameter("size", size)
            gachaTs?.let { parameter("gachaTs", it) }
            pos?.let { parameter("pos", it) }
            headers {
                append("X-Role-Token", u8Token.token)
                append(HttpHeaders.Cookie, "ak-user-center=${loginCookie.content}")
            }
        }
        return json.decodeFromString<GachaResponse>(response.bodyAsText()).data
    }

    class HgTokenExpired(val hgToken: HgToken) : Exception("HgToken 已过期")
    class HgTokenInvalid(val hgToken: HgToken, val msg: String = "HgToken 无效") : Exception(msg)

    @Serializable
    private data class BaseResponse(val status: Int, val msg: String)
    @Serializable
    private data class GrantAppTokenPayload(val appCode: String, val token: String, val type: Int)
    @Serializable
    data class AppToken(val hgId: String, val token: String)
    @Serializable
    private data class AppTokenResponse(val status: Int, val data: AppToken, val msg: String, val type: String)
    @Serializable
    data class AppBinding(val appCode: String, val appName: String, val bindingList: List<AccountInfo>)
    @Serializable
    data class MultiAppBindingList(val list: List<AppBinding>)
    @Serializable
    private data class BindingListResponse(val status: Int, val msg: String, val data: MultiAppBindingList)
    @Serializable
    data class U8Token(val token: String)
    @Serializable
    private data class U8TokenResponse(val status: Int, val msg: String, val data: U8Token? = null)
    data class LoginCookie(val content: String)
    @Serializable
    data class Pool(val id: String, val name: String)
    @Serializable
    private data class PoolListResponse(val code: Int, val msg: String, val data: List<Pool>)
    @Serializable
    data class GachaListData(val list: List<AgentGachaInfo>, val hasMore: Boolean)
    @Serializable
    private data class GachaResponse(val code: Int, val msg: String, val data: GachaListData)
}
