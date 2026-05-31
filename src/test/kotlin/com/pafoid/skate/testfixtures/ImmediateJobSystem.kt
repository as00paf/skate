package com.pafoid.skate.testfixtures

import com.pafoid.skate.engine.utils.IJobSystem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class ImmediateJobSystem : IJobSystem {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

    override fun isMainThread(): Boolean = true

    override fun update() = Unit

    override fun runAsync(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    override fun runOnMain(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    override fun <T> runAsyncDeferred(block: suspend CoroutineScope.() -> T): Deferred<T> =
        scope.async(block = block)

    override fun runIO(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    override fun destroy() {
        scope.coroutineContext[Job]?.cancel()
    }
}
