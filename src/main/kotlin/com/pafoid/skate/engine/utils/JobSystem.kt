package com.pafoid.skate.engine.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job

/**
 * Transitional compatibility facade for call sites that still use static JobSystem access.
 * New code should prefer injected IJobSystem.
 */
@Deprecated("Use injected IJobSystem instead of static JobSystem")
object JobSystem {
    private val delegate: IJobSystem = DefaultJobSystem()

    val Main: CoroutineDispatcher
        get() = delegate.mainDispatcher

    fun update() = delegate.update()

    fun runAsync(block: suspend CoroutineScope.() -> Unit): Job = delegate.runAsync(block)

    fun runOnMain(block: suspend CoroutineScope.() -> Unit): Job = delegate.runOnMain(block)

    fun <T> runAsyncDeferred(block: suspend CoroutineScope.() -> T): Deferred<T> = delegate.runAsyncDeferred(block)

    fun runIO(block: suspend CoroutineScope.() -> Unit): Job = delegate.runIO(block)

    fun destroy() = delegate.destroy()
}
