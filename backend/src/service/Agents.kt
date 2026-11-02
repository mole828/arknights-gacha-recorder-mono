package com.example.service

import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.classic.error
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.GachaService
import rpc.Collector

/** Collectors (remote agents or the local one) that take turns pulling tokens from a shared queue. */
class Agents(
    private val recorder: GachaService,
    private val taskInterval: Duration = 2.minutes,
) {
    private val connected = MutableStateFlow<Set<Collector>>(emptySet())
    private val pending = ArrayDeque<String>()
    private val queue = Mutex()
    private val log = Logger.of("Agents")

    val count: Int get() = connected.value.size
    fun available(): Collector? = connected.value.randomOrNull()

    suspend fun run(agent: Collector) {
        connected.update { it + agent }
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    val token = queue.withLock {
                        if (pending.isEmpty()) pending.addAll(recorder.activeTasks().shuffled().map { it.hgToken.content })
                        pending.removeFirstOrNull()
                    }
                    if (token != null) recorder.collect(agent, token)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("采集失败", e)
                }
                delay(taskInterval)
            }
        } finally {
            connected.update { it - agent }
        }
    }
}
