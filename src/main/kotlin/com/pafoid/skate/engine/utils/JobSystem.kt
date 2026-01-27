package com.pafoid.skate.engine.utils

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

object JobSystem {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        println("JobSystem Error: ${throwable.message}")
        throwable.printStackTrace()
    }

    private val mainThreadTasks = ConcurrentLinkedQueue<Runnable>()

    val Main: CoroutineDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            mainThreadTasks.add(block)
        }
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job + exceptionHandler + CoroutineName("SkateAsync"))

    /**
     * Executes pending tasks on the main thread.
     * Should be called from the main loop.
     */
    fun update() {
        while (mainThreadTasks.isNotEmpty()) {
            mainThreadTasks.poll()?.run()
        }
    }

    /**
     * Run a task asynchronously on the Default dispatcher.
     */
    fun runAsync(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(block = block)
    }

    /**
     * Run a task on the Main dispatcher (main thread).
     */
    fun runOnMain(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(Main, block = block)
    }

    /**
     * Run a task asynchronously and return a Deferred result.
     */
    fun <T> runAsyncDeferred(block: suspend CoroutineScope.() -> T): Deferred<T> {
        return scope.async(block = block)
    }

    /**
     * Run a task on the IO dispatcher (ideal for file loading).
     */
    fun runIO(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(Dispatchers.IO, block = block)
    }

    fun destroy() {
        job.cancel()
    }
}
