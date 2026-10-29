package com.pafoid.skate.engine.scripting.completion

import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/*
 * Script code-completion service (SCR-06).
 *
 * Extension point for hosts: implement [CompletionProvider] and return it from
 * [com.pafoid.skate.engine.scripting.host.ScriptHost.completionProvider]. The editor (EDT-26) only talks to
 * [CompletionService], which finds the provider by file extension through the [ScriptHostRegistry].
 * Providers must be pure functions of the [CompletionRequest]: no ImGui, GL or editor types, tolerant of
 * incomplete/invalid text, and must poll [CancellationToken.throwIfCancelled] in any long loop.
 */

enum class CompletionKind { PROPERTY, METHOD, VARIABLE, KEYWORD, SNIPPET }

/**
 * One completion item. Applying it replaces `text[replaceStart, replaceEnd)` with [insertText] and puts the caret at
 * `replaceStart + cursorOffset`.
 */
data class Completion(
    val label: String,
    val kind: CompletionKind,
    val insertText: String = label,
    val cursorOffset: Int = insertText.length,
    /** e.g. `fun find(name: String): ScriptEntity?`; null for keywords. */
    val signature: String? = null,
    val documentation: String? = null,
    /** Result/value type name, when known. */
    val type: String? = null,
    val replaceStart: Int,
    val replaceEnd: Int,
)

class CancellationToken {
    private val flag = AtomicBoolean(false)
    val isCancelled: Boolean get() = flag.get()
    fun cancel() = flag.set(true)
    fun throwIfCancelled() {
        if (isCancelled) throw java.util.concurrent.CancellationException("completion cancelled")
    }
}

data class CompletionRequest(
    val path: String,
    val text: String,
    /** Caret position as a 0-based char offset into [text]. */
    val offset: Int,
    /** True for an explicit invocation (Ctrl+Space): also completes with an empty prefix at top level. */
    val explicit: Boolean = true,
    val maxResults: Int = 100,
    val cancellation: CancellationToken = CancellationToken(),
)

interface CompletionProvider {
    /** Never throws for malformed text; returns an empty list when nothing applies. */
    fun complete(request: CompletionRequest): List<Completion>
}

/** A running request; [cancel] aborts it (the future then completes with CancellationException). */
class CompletionHandle internal constructor(
    val result: CompletableFuture<List<Completion>>,
    private val token: CancellationToken,
) {
    fun cancel() {
        token.cancel()
        result.cancel(true)
    }
}

/**
 * Runs completion off the caller's thread (never call providers on the render/UI thread). A new request cancels
 * the previous one so typing quickly never queues stale work.
 */
class CompletionService(
    private val hosts: ScriptHostRegistry,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "skate-script-completion").apply { isDaemon = true }
    },
) : AutoCloseable {
    private var current: CompletionHandle? = null

    @Synchronized
    fun request(request: CompletionRequest): CompletionHandle {
        current?.cancel()
        val provider = hosts.completionProviderFor(request.path)
        val future = CompletableFuture<List<Completion>>()
        val handle = CompletionHandle(future, request.cancellation)
        current = handle
        executor.execute {
            if (future.isDone) return@execute
            try {
                future.complete(provider?.complete(request).orEmpty())
            } catch (c: java.util.concurrent.CancellationException) {
                future.cancel(true)
            } catch (t: Throwable) {
                future.complete(emptyList())
            }
        }
        return handle
    }

    override fun close() {
        current?.cancel()
        executor.shutdownNow()
    }
}
