package com.pafoid.skate.engine.utils

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

class DefaultJobSystem(
    private val maxMainThreadTasksPerUpdate: Int = DEFAULT_MAX_MAIN_THREAD_TASKS_PER_UPDATE
) : IJobSystem {
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

    override val mainDispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            mainThreadTasks.add(block)
        }
    }

    override fun isMainThread(): Boolean = Thread.currentThread().id == mainThreadId

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job + exceptionHandler + CoroutineName("SkateAsync"))

    override fun update() {
        repeat(maxMainThreadTasksPerUpdate) {
            val task = mainThreadTasks.poll() ?: return
            task.run()
        }
    }

    override fun runAsync(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(block = block)
    }

    override fun runOnMain(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(mainDispatcher, block = block)
    }

    override fun <T> runAsyncDeferred(block: suspend CoroutineScope.() -> T): Deferred<T> {
        return scope.async(block = block)
    }

    override fun runIO(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(Dispatchers.IO, block = block)
    }

    override fun destroy() {
        job.cancel()
    }

    companion object {
        private const val DEFAULT_MAX_MAIN_THREAD_TASKS_PER_UPDATE = 256
    }
}
