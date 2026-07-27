package com.pafoid.skate.engine.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

class JobSystem(
    private val maxMainThreadTasksPerUpdate: Int = DEFAULT_MAX_MAIN_THREAD_TASKS_PER_UPDATE
) {
    init {
        require(maxMainThreadTasksPerUpdate > 0) {
            "maxMainThreadTasksPerUpdate must be > 0"
        }
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        println("JobSystem Error: ${throwable.message}")
        throwable.printStackTrace()
    }

    private val mainThreadTasks = ConcurrentLinkedQueue<Runnable>()
    private val mainThreadId: Long = Thread.currentThread().id

    val mainDispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            mainThreadTasks.add(block)
        }
    }

    fun isMainThread(): Boolean = Thread.currentThread().id == mainThreadId

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job + exceptionHandler + CoroutineName("SkateAsync"))

    fun update() {
        repeat(maxMainThreadTasksPerUpdate) {
            val task = mainThreadTasks.poll() ?: return
            task.run()
        }
    }

    fun runAsync(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(block = block)
    }

    fun runOnMain(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(mainDispatcher, block = block)
    }

    fun <T> runAsyncDeferred(block: suspend CoroutineScope.() -> T): Deferred<T> {
        return scope.async(block = block)
    }

    fun runIO(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(Dispatchers.IO, block = block)
    }

    fun destroy() {
        job.cancel()
    }

    companion object {
        private const val DEFAULT_MAX_MAIN_THREAD_TASKS_PER_UPDATE = 256
    }
}