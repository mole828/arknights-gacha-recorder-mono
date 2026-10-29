package model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import rpc.AgentGachaInfo
import rpc.Collector
import rpc.CollectorResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GachaServiceTest {
    private val account = AccountInfo(1, "官服", true, false, true, "Doctor", "uid")

    @Test
    fun parsesPlainAndCopiedTokensWithoutChangingTheirContents() {
        assertEquals("token", parseRegistrationToken(" token "))
        assertEquals("token", parseRegistrationToken("""{"code":0,"data":{"content":"token"},"msg":"ok"}"""))
        assertEquals("{invalid json}", parseRegistrationToken("{invalid json}"))
        assertFailsWith<IllegalArgumentException> { parseRegistrationToken(" ") }
        assertFailsWith<IllegalArgumentException> {
            parseRegistrationToken("""{"code":0,"data":{"content":" "},"msg":"ok"}""")
        }
    }

    @Test
    fun registrationSavesTheAccountWithoutStartingCollection() = runBlocking {
        val repository = MemoryRepository()
        val collector = object : Collector {
            override suspend fun validate(token: String) = CollectorResult.Success(account)
            override suspend fun collect(token: String): CollectorResult = error("registration must not collect")
        }
        val service = GachaService(repository)
        assertEquals(RegisterResponse("记录成功，后续会陆续补全", "uid"), service.register("token", collector))
        assertEquals(listOf(account to HgToken("token")), repository.accounts)
    }

    @Test
    fun collectionKeepsAccountFieldsAndMarksTokenActiveOnlyAfterSaving() = runBlocking {
        val repository = MemoryRepository()
        val gacha = AgentGachaInfo("char", "Operator", ULong.MAX_VALUE, true, "pool", "Banner", 9u, 5u)
        val result = CollectorResult.Success(account, listOf(gacha))
        val service = GachaService(repository)
        assertEquals(result, service.collect(collector(result), "token"))
        assertEquals(listOf(GachaRecord("uid", ULong.MAX_VALUE, 9u, "char", "Operator", "pool", "Banner", 5u, true)), repository.records)
        assertEquals(listOf(HgToken("token") to false), repository.expiryUpdates)

        repository.expiryUpdates.clear()
        repository.failRecord = true
        assertFailsWith<IllegalStateException> { service.collect(collector(result), "token") }
        assertTrue(repository.expiryUpdates.isEmpty())
    }

    @Test
    fun rejectedCollectionExpiresTokenAndCancellationPropagates() = runBlocking {
        val repository = MemoryRepository()
        val service = GachaService(repository)
        for (result in listOf(CollectorResult.Expired, CollectorResult.Invalid("invalid"))) {
            assertEquals(result, service.collect(collector(result), "token"))
        }
        assertEquals(List(2) { HgToken("token") to true }, repository.expiryUpdates)
        assertTrue(repository.accounts.isEmpty())
        val cancelled = object : Collector {
            override suspend fun validate(token: String): CollectorResult = throw CancellationException()
            override suspend fun collect(token: String): CollectorResult = throw CancellationException()
        }
        assertFailsWith<CancellationException> { service.register("token", cancelled) }
        assertFailsWith<CancellationException> { service.collect(cancelled, "token") }
        assertTrue(repository.accounts.isEmpty())
    }

    private fun collector(result: CollectorResult) = object : Collector {
        override suspend fun validate(token: String) = result
        override suspend fun collect(token: String) = result
    }
}

private class MemoryRepository : GachaRepository {
    val accounts = mutableListOf<Pair<AccountInfo, HgToken>>()
    val records = mutableListOf<GachaRecord>()
    val expiryUpdates = mutableListOf<Pair<HgToken, Boolean>>()
    var failRecord = false

    override suspend fun upsertUser(accountInfo: AccountInfo, hgToken: HgToken) { accounts += accountInfo to hgToken }
    override suspend fun updateUser(accountInfo: AccountInfo) = Unit
    override suspend fun expireUid(uid: Uid) = Unit
    override suspend fun expireToken(hgToken: HgToken) = setTokenExpired(hgToken, true)
    override suspend fun setTokenExpired(hgToken: HgToken, expired: Boolean) { expiryUpdates += hgToken to expired }
    override suspend fun activeTokens(): List<UserToken> = emptyList()
    override suspend fun users(): List<AccountInfo> = accounts.map { it.first }
    override suspend fun expiredUsers(): List<AccountInfo> = emptyList()
    override suspend fun gachaPage(uid: Uid?, page: Long, pageSize: Int) =
        GachasResponse(GachasResponseData(emptyList(), Pagination(page.toULong(), pageSize, 0uL)))
    override suspend fun record(gacha: GachaRecord): UInt {
        check(!failRecord) { "save failed" }
        records += gacha
        return 1u
    }
}
