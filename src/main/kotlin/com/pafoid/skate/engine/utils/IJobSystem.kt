package com.pafoid.skate.engine.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job

interface IJobSystem {
    val mainDispatcher: CoroutineDispatcher

    fun isMainThread(): Boolean

    fun update()

    fun runAsync(block: suspend CoroutineScope.() -> Unit): Job

    fun runOnMain(block: suspend CoroutineScope.() -> Unit): Job

    fun <T> runAsyncDeferred(block: suspend CoroutineScope.() -> T): Deferred<T>

    fun runIO(block: suspend CoroutineScope.() -> Unit): Job

    fun destroy()
}
