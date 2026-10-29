package com.example

import api.ArkNights
import com.example.service.GachaRecorder
import com.example.service.GachaRepository
import com.example.service.UserToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.rpc.registerService
import kotlinx.rpc.krpc.rpcServerConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.krpc.server.KrpcServer
import model.GachasResponse
import model.GachasResponseData
import model.Pagination
import model.RegisterRequest
import model.RegisterResponse
import rpc.AgentAccountInfo
import rpc.AgentGachaInfo
import rpc.Collector
import rpc.CollectorResult
import rpc.asKrpcTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AgentRpcIntegrationTest {
    @Test
    fun invalidPagesReturnBadRequest() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            configureRouting(GachaRecorder(RecordingRepository()), AGENT_KEY)
        }
        for (page in listOf("-1", "not-a-number", Long.MAX_VALUE.toString())) {
            assertEquals(HttpStatusCode.BadRequest, client.get("/gachas?page=$page").status)
        }
        assertEquals(HttpStatusCode.OK, client.get("/gachas?page=0").status)
    }

    @Test
    fun emptyCollectionRoundWaitsAndCanBeCancelled() = runBlocking {
        var rounds = 0
        val repository = RecordingRepository(beforeActiveTokens = { rounds++ })
        val recorder = GachaRecorder(repository)
        val loop = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            recorder.mainLoop { 60.seconds }
        }
        try {
            assertEquals(1, rounds)
        } finally {
            loop.cancelAndJoin()
        }
    }

    @Test
    fun registrationSendsHeadersBeforeValidationAndReturnsBeforeBackgroundSave() = runBlocking {
        val allowValidation = CompletableDeferred<Unit>()
        val saving = CompletableDeferred<Unit>()
        val allowSave = CompletableDeferred<Unit>()
        val repository = RecordingRepository(beforeRecord = {
            saving.complete(Unit)
            allowSave.await()
        })
        val token = "streamed-registration"
        val collector = TestCollector(
            validateResult = {
                allowValidation.await()
                CollectorResult.Success(account(it))
            },
            collectResult = { CollectorResult.Success(account(it), listOf(gacha(it))) },
        )
        val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
            install(ContentNegotiation) { json() }
            configureRouting(GachaRecorder(repository), AGENT_KEY)
        }.start(wait = false)
        val port = server.engine.resolvedConnectors().single().port
        val http = HttpClient(ClientCIO) {
            install(WebSockets)
            defaultRequest { url("http://127.0.0.1:$port") }
        }
        try {
            withTimeout(10.seconds) {
                val connection = startAgent(http, AGENT_KEY, collector)
                connection.ready.await()
                awaitConnected(http, 1)
                val headersReceived = CompletableDeferred<Unit>()
                val body = async {
                    http.preparePost("/register") {
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(RegisterRequest(token)))
                    }.execute { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                        headersReceived.complete(Unit)
                        Json.decodeFromString<RegisterResponse>(response.bodyAsText())
                    }
                }
                // Validation cannot finish until the client has received the headers.
                headersReceived.await()
                assertFalse(body.isCompleted)
                assertTrue(repository.upserts.tryReceive().isFailure)
                allowValidation.complete(Unit)

                saving.await()
                assertEquals(RegisterResponse("记录成功，后续会陆续补全", account(token).uid), body.await())
                assertTrue(repository.gachas.tryReceive().isFailure)
                allowSave.complete(Unit)
                assertEquals(account(token).uid, repository.gachas.receiveWithin().uid)
                assertEquals(ArkNights.HgToken(token) to false, repository.expiryUpdates.receiveWithin())
                connection.job.cancelAndJoin()
            }
        } finally {
            http.close()
            server.stop(0, 1000)
        }
    }

    @Test
    fun registrationCallsAgentOverReverseRpcAndPersistsAccountAndGachas() = testApplication {
        val repository = RecordingRepository()
        val token = "token-one"
        val account = account(token)
        val collector = TestCollector(
            validateResult = { CollectorResult.Success(account) },
            collectResult = { CollectorResult.Success(account, listOf(gacha(token))) },
        )
        val recorder = GachaRecorder(repository)
        application { install(ContentNegotiation) { json() }; configureRouting(recorder, AGENT_KEY) }
        val agentClient = createClient { install(WebSockets) }

        try {
            coroutineScope {
                val connection = startAgent(agentClient, AGENT_KEY, collector)
                withTimeout(3.seconds) { connection.ready.await() }
                awaitConnected(client, 1)

                val pasted = Json.encodeToString(ArkNights.HgTokenResponse(0, ArkNights.HgToken(token), "ok"))
                val response = client.register("  $pasted  ")
                assertEquals("记录成功，后续会陆续补全", responseMessage(response))
                assertEquals(account.uid, Json.decodeFromString<RegisterResponse>(response).uid)
                assertEquals(setOf("msg", "uid"), Json.parseToJsonElement(response).jsonObject.keys)
                assertEquals(token, collector.validatedTokens.receiveWithin())
                assertEquals(token, collector.collectedTokens.receiveWithin())

                // Registration validates and upserts first; collection then refreshes the same account.
                val upserts = List(2) { repository.upserts.receiveWithin() }
                assertTrue(upserts.all { it.hgToken.content == token && it.account.uid == account.uid })
                val stored = repository.gachas.receiveWithin()
                assertEquals(account.uid, stored.uid)
                assertEquals("char-$token", stored.charId)
                assertEquals("Operator $token", stored.charName)
                assertEquals(1_700_000_000_000uL, stored.gachaTs)
                assertEquals("pool-$token", stored.poolId)
                assertEquals(2u, stored.pos)
                assertEquals(6u, stored.rarity)
                assertEquals(ArkNights.HgToken(token) to false, repository.expiryUpdates.receiveWithin())

                connection.job.cancelAndJoin()
                awaitConnected(client, 0)
            }
        } finally {
            agentClient.close()
        }
    }

    @Test
    fun concurrentRegistrationsKeepEachTokenPairedWithItsAccount() = testApplication {
        val repository = RecordingRepository()
        val validationCount = java.util.concurrent.atomic.AtomicInteger()
        val bothValidationsArrived = CompletableDeferred<Unit>()
        val collector = TestCollector(
            validateResult = { token ->
                if (validationCount.incrementAndGet() == 2) bothValidationsArrived.complete(Unit)
                withTimeout(3.seconds) { bothValidationsArrived.await() }
                CollectorResult.Success(account(token))
            },
            collectResult = { token -> CollectorResult.Success(account(token)) },
        )
        application { install(ContentNegotiation) { json() }; configureRouting(GachaRecorder(repository), AGENT_KEY) }
        val agentClient = createClient { install(WebSockets) }

        try {
            coroutineScope {
                val connection = startAgent(agentClient, AGENT_KEY, collector)
                withTimeout(3.seconds) { connection.ready.await() }
                awaitConnected(client, 1)

                val tokens = listOf("alpha", "beta")
                val responses = tokens.map { token ->
                    async { client.register(token) }
                }.awaitAll()
                assertTrue(responses.all { responseMessage(it) == "记录成功，后续会陆续补全" })
                assertEquals(tokens.sorted(), List(2) { collector.validatedTokens.receiveWithin() }.sorted())
                assertEquals(tokens.sorted(), List(2) { collector.collectedTokens.receiveWithin() }.sorted())

                val upserts = List(4) { repository.upserts.receiveWithin() }
                assertTrue(upserts.all { it.account.uid == "uid-${it.hgToken.content}" })
                assertEquals(tokens.toSet(), upserts.map { it.hgToken.content }.toSet())
                assertEquals(2, upserts.count { it.hgToken.content == "alpha" })
                assertEquals(2, upserts.count { it.hgToken.content == "beta" })

                connection.job.cancelAndJoin()
                awaitConnected(client, 0)
            }
        } finally {
            agentClient.close()
        }
    }

    @Test
    fun expiredAndInvalidTokensReturnAgentResultsWithoutRegistering() = testApplication {
        val repository = RecordingRepository()
        val collector = TestCollector(
            validateResult = { token ->
                when (token) {
                    "expired" -> CollectorResult.Expired
                    else -> CollectorResult.Invalid("not valid")
                }
            },
            collectResult = { error("collect must not run for rejected registrations") },
        )
        application { install(ContentNegotiation) { json() }; configureRouting(GachaRecorder(repository), AGENT_KEY) }
        val agentClient = createClient { install(WebSockets) }

        try {
            coroutineScope {
                val connection = startAgent(agentClient, AGENT_KEY, collector)
                withTimeout(3.seconds) { connection.ready.await() }
                awaitConnected(client, 1)

                val expired = client.register("expired")
                val invalid = client.register("invalid")
                assertEquals("HgToken 已过期", responseMessage(expired))
                assertEquals("HgToken 无效", responseMessage(invalid))
                assertEquals(listOf("expired", "invalid"), List(2) { collector.validatedTokens.receiveWithin() })
                assertTrue(collector.collectedTokens.tryReceive().isFailure)
                assertTrue(repository.upserts.tryReceive().isFailure)

                connection.job.cancelAndJoin()
                awaitConnected(client, 0)
            }
        } finally {
            agentClient.close()
        }
    }

    @Test
    fun backgroundCollectionFailuresDoNotFailRegistration() = testApplication {
        val saveAttempted = CompletableDeferred<Unit>()
        val repository = RecordingRepository(beforeRecord = {
            saveAttempted.complete(Unit)
            error("simulated database failure")
        })
        val collector = TestCollector(
            validateResult = { CollectorResult.Success(account(it)) },
            collectResult = { token ->
                when (token) {
                    "expired" -> CollectorResult.Expired
                    "invalid" -> CollectorResult.Invalid("not valid")
                    else -> CollectorResult.Success(account(token), listOf(gacha(token)))
                }
            },
        )
        application { install(ContentNegotiation) { json() }; configureRouting(GachaRecorder(repository), AGENT_KEY) }
        val agentClient = createClient { install(WebSockets) }
        try {
            coroutineScope {
                val connection = startAgent(agentClient, AGENT_KEY, collector)
                connection.ready.await()
                awaitConnected(client, 1)
                for (token in listOf("expired", "invalid", "save-failed")) {
                    assertEquals(RegisterResponse("记录成功，后续会陆续补全", account(token).uid),
                        Json.decodeFromString<RegisterResponse>(client.register(token)))
                }
                withTimeout(3.seconds) { saveAttempted.await() }
                assertEquals(setOf("expired", "invalid", "save-failed"),
                    List(3) { collector.collectedTokens.receiveWithin() }.toSet())
                val expiredTokens = List(2) { repository.expiryUpdates.receiveWithin() }
                assertTrue(expiredTokens.all { it.second })
                assertEquals(setOf("expired", "invalid"), expiredTokens.map { it.first.content }.toSet())
                assertTrue(repository.gachas.tryReceive().isFailure)
                connection.job.cancelAndJoin()
            }
        } finally {
            agentClient.close()
        }
    }

    @Test
    fun rejectsWrongAgentKeyAndRemovesDisconnectedAgent() = testApplication {
        val repository = RecordingRepository()
        application { install(ContentNegotiation) { json() }; configureRouting(GachaRecorder(repository), AGENT_KEY) }
        val agentClient = createClient { install(WebSockets) }

        try {
            var rejected: CloseReason? = null
            withTimeout(3.seconds) {
                agentClient.webSocket("/agent/ws", request = {
                    header(HttpHeaders.Authorization, "Bearer wrong-key")
                }) {
                    rejected = closeReason.await()
                }
            }
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, rejected?.code)
            awaitConnected(client, 0)
            assertTrue(repository.upserts.tryReceive().isFailure)

            coroutineScope {
                val connection = startAgent(agentClient, AGENT_KEY, TestCollector(
                    validateResult = { CollectorResult.Invalid("unused") },
                    collectResult = { CollectorResult.Invalid("unused") },
                ))
                withTimeout(3.seconds) { connection.ready.await() }
                awaitConnected(client, 1)
                connection.job.cancelAndJoin()
                awaitConnected(client, 0)
            }
        } finally {
            agentClient.close()
        }
    }

    @Test
    fun registrationRejectsEmptyBodyTokenAndDoesNotAcceptQueryTokens() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            configureRouting(GachaRecorder(RecordingRepository()), AGENT_KEY)
        }
        val blank = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("  ")))
        }
        assertEquals(HttpStatusCode.BadRequest, blank.status)
        val queryOnly = client.post("/register?token=must-not-be-read") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.BadRequest, queryOnly.status)
        assertEquals(RegisterResponse("记录员未连接，请稍后重试"),
            Json.decodeFromString<RegisterResponse>(client.register("token")))
    }
}

private const val AGENT_KEY = "test-agent-key"

private data class Upsert(val account: ArkNights.AccountInfo, val hgToken: ArkNights.HgToken)

private class RecordingRepository(
    private val beforeRecord: suspend () -> Unit = {},
    private val beforeActiveTokens: () -> Unit = {},
) : GachaRepository {
    val upserts = Channel<Upsert>(Channel.UNLIMITED)
    val gachas = Channel<ArkNights.GachaApi.Gacha>(Channel.UNLIMITED)
    val expiryUpdates = Channel<Pair<ArkNights.HgToken, Boolean>>(Channel.UNLIMITED)

    override suspend fun initSchema() = Unit
    override suspend fun upsertUser(accountInfo: ArkNights.AccountInfo, hgToken: ArkNights.HgToken) {
        upserts.send(Upsert(accountInfo, hgToken))
    }
    override suspend fun updateUser(accountInfo: ArkNights.AccountInfo) = Unit
    override suspend fun expireUid(uid: model.Uid) = Unit
    override suspend fun expireToken(hgToken: ArkNights.HgToken) = Unit
    override suspend fun setTokenExpired(hgToken: ArkNights.HgToken, expired: Boolean) {
        expiryUpdates.send(hgToken to expired)
    }
    override suspend fun activeTokens(): List<UserToken> {
        beforeActiveTokens()
        return emptyList()
    }
    override suspend fun users(): List<ArkNights.AccountInfo> = emptyList()
    override suspend fun expiredUsers(): List<ArkNights.AccountInfo> = emptyList()
    override suspend fun gachaPage(uid: model.Uid?, page: Long, pageSize: Int): GachasResponse =
        GachasResponse(GachasResponseData(emptyList(), Pagination(0uL, pageSize, 0uL)))
    override suspend fun record(gacha: ArkNights.GachaApi.Gacha): UInt {
        beforeRecord()
        gachas.send(gacha)
        return 1u
    }
}

private class TestCollector(
    private val validateResult: suspend (String) -> CollectorResult,
    private val collectResult: suspend (String) -> CollectorResult,
) : Collector {
    val validatedTokens = Channel<String>(Channel.UNLIMITED)
    val collectedTokens = Channel<String>(Channel.UNLIMITED)

    override suspend fun validate(token: String): CollectorResult {
        validatedTokens.send(token)
        return validateResult(token)
    }

    override suspend fun collect(token: String): CollectorResult {
        collectedTokens.send(token)
        return collectResult(token)
    }
}

private data class AgentConnection(val job: kotlinx.coroutines.Job, val ready: CompletableDeferred<Unit>)

private fun CoroutineScope.startAgent(client: HttpClient, key: String, collector: Collector): AgentConnection {
    val ready = CompletableDeferred<Unit>()
    val job = launch {
        client.webSocket("/agent/ws", request = {
            header(HttpHeaders.Authorization, "Bearer $key")
        }) {
            val server = object : KrpcServer(
                rpcServerConfig { serialization { json() } },
                asKrpcTransport(),
            ) {}
            server.registerService<Collector> { collector }
            ready.complete(Unit)
            try {
                closeReason.await()
            } finally {
                server.close("Test WebSocket session ended")
            }
        }
    }
    return AgentConnection(job, ready)
}

private suspend fun awaitConnected(client: HttpClient, expected: Int) {
    withTimeout(3.seconds) {
        while (true) {
            val body = client.get("/agent/status").bodyAsText()
            val connected = Json.parseToJsonElement(body).jsonObject.getValue("connected").jsonPrimitive.int
            if (connected == expected) return@withTimeout
            delay(10)
        }
    }
}

private suspend fun <T> Channel<T>.receiveWithin(): T = withTimeout(3.seconds) { receive() }

private fun responseMessage(body: String): String =
    Json.parseToJsonElement(body).jsonObject.getValue("msg").jsonPrimitive.content

private fun account(token: String) = AgentAccountInfo(
    channelMasterId = 1,
    channelName = "官服",
    isDefault = true,
    isDeleted = false,
    isOfficial = true,
    nickName = "Operator $token",
    uid = "uid-$token",
)

private fun gacha(token: String) = AgentGachaInfo(
    charId = "char-$token",
    charName = "Operator $token",
    gachaTs = 1_700_000_000_000uL,
    isNew = true,
    poolId = "pool-$token",
    poolName = "Pool $token",
    pos = 2u,
    rarity = 6u,
)

private suspend fun HttpClient.register(token: String): String =
    post("/register") {
        contentType(ContentType.Application.Json)
        setBody(Json.encodeToString(RegisterRequest(token)))
    }.bodyAsText()
