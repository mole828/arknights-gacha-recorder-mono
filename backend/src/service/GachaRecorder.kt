package com.example.service

import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.classic.error
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import model.GachaRepository
import model.GachaService
import rpc.Collector

class GachaRecorder(repository: GachaRepository) {
    private val logger = Logger.of(GachaRecorder::class)

    val scope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() +
            CoroutineExceptionHandler { _, throwable ->
                logger.error("coroutine failed", throwable)
            }
    )

    val records = GachaService(repository)
    val agents = Agents(records)

    /** Runs [collector] in-process, sharing the task queue with remote agents. */
    fun startLocalCollector(collector: Collector) {
        scope.launch { agents.run(collector) }
    }

    fun mainLoopRunning(): Map<String, Boolean> {
        val job = requireNotNull(scope.coroutineContext[Job])
        return job.children.toList().associate {
            it.key.toString() to it.isActive
        }
    }
}
