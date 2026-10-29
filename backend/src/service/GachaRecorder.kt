package com.example.service

import client.ArkNights
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.classic.debug
import io.github.smyrgeorge.log4k.classic.error
import io.github.smyrgeorge.log4k.classic.info
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import model.GachaRecord
import model.GachaRepository
import model.GachaService
import model.HgToken

class GachaRecorder(
    private val repository: GachaRepository,
    private val arkCenterApi: ArkNights,
) {
    private val logger = Logger.of(GachaRecorder::class)

    val scope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() +
            CoroutineExceptionHandler { _, throwable ->
                logger.error("coroutine failed", throwable)
            }
    )

    val records = GachaService(repository)

    suspend fun updateGacha(hgToken: HgToken, size: UInt = 10u): UInt {
        logger.debug("checking hgToken")
        require(arkCenterApi.checkToken(hgToken)) {
            repository.expireToken(hgToken)
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
        repository.updateUser(account)

        logger.debug("${account.nickName} loading u8 token")
        val u8Token = arkCenterApi.u8TokenByUid(appToken, account.uid)

        logger.debug("${account.nickName} logging in")
        val loginCookie = arkCenterApi.login(u8Token)

        logger.debug("${account.nickName} loading pool list")
        val poolList = arkCenterApi.poolList(account.uid, u8Token, loginCookie)

        val waitInsert = mutableListOf<GachaRecord>()
        poolList.forEach { pool ->
            val poolName = pool.name.replace("\n", "-")
            logger.debug("${account.nickName} loading $poolName history")

            var history = arkCenterApi.history(loginCookie, u8Token, pool, size = size)
            while (true) {
                waitInsert.addAll(history.list.map { GachaRecord(
                    uid = account.uid, gachaTs = it.gachaTs, pos = it.pos,
                    charId = it.charId, charName = it.charName,
                    poolId = it.poolId, poolName = it.poolName,
                    rarity = it.rarity, isNew = it.isNew,
                ) })

                if (!history.hasMore) break
                delay(5.seconds)

                val last = history.list.lastOrNull() ?: break
                logger.debug("${account.nickName} loading $poolName history, processed ${waitInsert.size} rows")
                history = arkCenterApi.history(
                    loginCookie = loginCookie,
                    u8Token = u8Token,
                    pool = pool,
                    size = size,
                    gachaTs = last.gachaTs,
                    pos = last.pos,
                )
            }
        }

        return records.record(waitInsert)
    }

    suspend fun mainLoop(delayTime: suspend () -> Duration = { 2.minutes }) {
        while (true) {
            val roundBeginTime = Clock.System.now()
            logger.info("开始抓取用户数据")

            val hgTokenMap = repository.activeTokens().shuffled()
            if (hgTokenMap.isEmpty()) {
                delay(delayTime())
                continue
            }
            var total = 0u
            hgTokenMap.forEach { token ->
                logger.info("更新用户数据, nickName: ${token.nickName ?: token.uid}")
                val result = try {
                    updateGacha(token.hgToken)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
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
