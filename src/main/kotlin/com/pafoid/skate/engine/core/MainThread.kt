package com.pafoid.skate.engine.core

import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks the engine's main thread so scene mutation can be asserted to happen on it.
 * Unbound (the default, e.g. in unit tests) means every thread is accepted.
 * Async jobs must never mutate the scene; they post via [JobSystem.runOnMain].
 */
object MainThread {
    private val thread = AtomicReference<Thread?>(null)
    private val reported = Collections.synchronizedSet(HashSet<String>())

    /** Receives the description of each violation (once per distinct description). */
    @Volatile
    var violationHandler: (String) -> Unit = {}

    /** When true a violation throws [IllegalStateException] instead of only being reported. */
    @Volatile
    var strict: Boolean = false

    fun bind(mainThread: Thread = Thread.currentThread()) {
        thread.set(mainThread)
        reported.clear()
    }

    fun unbind() {
        thread.set(null)
        reported.clear()
    }

    fun isMainThread(): Boolean {
        val main = thread.get() ?: return true
        return Thread.currentThread() === main
    }

    fun assertOnMainThread(what: String) {
        if (isMainThread()) return
        val message = "$what must run on the main thread (was ${Thread.currentThread().name})"
        if (strict) throw IllegalStateException(message)
        if (reported.add(message)) violationHandler(message)
    }
}
