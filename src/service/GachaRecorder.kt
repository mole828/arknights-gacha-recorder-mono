package com.example.service

import api.ArkNights
import api.Uid
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.classic.debug
import io.github.smyrgeorge.log4k.classic.error
import io.github.smyrgeorge.log4k.classic.info
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import model.GachasResponse
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class GachaRecorder(
    private val repository: GachaRepository,
    private val arkCenterApi: ArkNights = ArkNights.default(),
    private val gachaApi: ArkNights.GachaApi = ArkNights.GachaApi.default(),
) {
    private val logger = Logger.of(GachaRecorder::class)

    val scope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() +
            CoroutineExceptionHandler { _, throwable ->
                logger.error("coroutine failed", throwable)
            }
    )

    suspend fun init() {
        repository.initSchema()
    }

    suspend fun upsert(accountInfo: ArkNights.AccountInfo, hgToken: ArkNights.HgToken) {
        repository.upsertUser(accountInfo, hgToken)
    }

    suspend fun update(accountInfo: ArkNights.AccountInfo) {
        repository.updateUser(accountInfo)
    }

    suspend fun expire(uid: Uid) {
        repository.expireUid(uid)
    }

    suspend fun expire(hgToken: ArkNights.HgToken) {
        repository.expireToken(hgToken)
    }

    suspend fun setTokenExpired(hgToken: ArkNights.HgToken, expired: Boolean) {
        repository.setTokenExpired(hgToken, expired)
    }

    suspend fun users(): List<ArkNights.AccountInfo> = repository.users()

    suspend fun expiredUsers(): List<ArkNights.AccountInfo> = repository.expiredUsers()

    suspend fun activeTasks(): List<UserToken> = repository.activeTokens()

    suspend fun gachaPage(uid: Uid?, page: Long, pageSize: Int): GachasResponse =
        repository.gachaPage(uid, page, pageSize)

    suspend fun record(gacha: ArkNights.GachaApi.Gacha): UInt = repository.record(gacha)

    suspend fun record(list: List<ArkNights.GachaApi.Gacha>): UInt {
        var total = 0u
        list.forEach {
            total += record(it)
        }
        return total
    }

    suspend fun updateGacha(hgToken: ArkNights.HgToken, size: UInt = 10u): UInt {
        logger.debug("checking hgToken")
        require(arkCenterApi.checkToken(hgToken)) {
            expire(hgToken)
            "hgToken 无效"
        }

        logger.debug("granting app token")
        val appToken = arkCenterApi.grantAppToken(hgToken)

        logger.debug("loading binding list")
        val bindingList = arkCenterApi.bindingList(appToken = appToken)
        val appBindings = bindingList.list.first()
        require(appBindings.appCode == "arknights") {
            "这是什么? $appBindings"
        }

        val account = appBindings.bindingList.first()
        update(account)

        logger.debug("${account.nickName} loading u8 token")
        val u8Token = arkCenterApi.u8TokenByUid(appToken, account.uid)

        logger.debug("${account.nickName} logging in")
        val loginCookie = arkCenterApi.login(u8Token)

        logger.debug("${account.nickName} loading pool list")
        val poolList = gachaApi.poolList(account.uid, u8Token, loginCookie)

        val waitInsert = mutableListOf<ArkNights.GachaApi.Gacha>()
        poolList.forEach { pool ->
            val poolName = pool.name.replace("\n", "-")
            logger.debug("${account.nickName} loading $poolName history")

            var history = gachaApi.history(loginCookie, u8Token, account.uid, pool, size = size)
            while (true) {
                waitInsert.addAll(history.list.map { ArkNights.GachaApi.Gacha.from(account.uid, it) })

                if (!history.hasMore) break
                delay(5.seconds)

                val last = history.list.lastOrNull() ?: break
                logger.debug("${account.nickName} loading $poolName history, processed ${waitInsert.size} rows")
                history = gachaApi.history(
                    loginCookie = loginCookie,
                    u8Token = u8Token,
                    uid = account.uid,
                    pool = pool,
                    size = size,
                    gachaTs = last.gachaTs,
                    pos = last.pos,
                )
            }
        }

        return record(waitInsert)
    }

    suspend fun mainLoop(delayTime: suspend () -> Duration = { 2.minutes }) {
        while (true) {
            val roundBeginTime = Clock.System.now()
            logger.info("开始抓取用户数据")

            val hgTokenMap = repository.activeTokens().shuffled()
            var total = 0u
            hgTokenMap.forEach { token ->
                logger.info("更新用户数据, nickName: ${token.nickName ?: token.uid}")
                val result = try {
                    updateGacha(token.hgToken)
                } catch (e: Throwable) {
                    logger.error("更新用户数据失败", e)
                    0u
                }
                delay(delayTime())
                total += result
            }

            logger.info("本轮更新用户数据, total: $total")
            logger.info("本轮更新用户数据, 耗时: ${Clock.System.now() - roundBeginTime}")
        }
    }

    fun mainLoopRunning(): Map<String, Boolean> {
        val job = scope.coroutineContext[Job]
        requireNotNull(job)
        return job.children.toList().associate {
            it.key.toString() to it.isActive
        }
    }
}
