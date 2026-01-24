package com.pafoid.skate.engine.utils

import kotlinx.coroutines.*

object JobSystem {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    fun runAsync(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(block = block)
    }

    fun destroy() {
        job.cancel()
    }
}