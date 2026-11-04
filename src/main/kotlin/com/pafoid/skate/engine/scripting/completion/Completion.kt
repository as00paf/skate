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

    /** Parameter hints for the call the caret is inside, or null. Same threading rules as [complete]. */
    fun signatureHelp(request: CompletionRequest): SignatureHelp? = null

    /** Language facts the editor needs for popup triggering and bracket handling. */
    val capabilities: CompletionCapabilities get() = CompletionCapabilities()

    /** Documented script API (types, members, signatures, docs) for a reference/tooltip panel. */
    fun apiReference(): List<ApiTypeDoc> = emptyList()
}

data class CompletionCapabilities(
    /** Typing one of these opens the popup automatically. Identifier characters always do as well. */
    val triggerCharacters: Set<Char> = setOf('.'),
    /** Typing the key inserts key+value and leaves the caret between; typing the value over itself skips it. */
    val autoClosePairs: Map<Char, Char> = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\''),
    val lineComment: String = "//",
)

/** Parameter hint: [label] is the whole signature, [parameters] its parameters, [activeParameter] the caret's one (0-based). */
data class SignatureHelp(
    val label: String,
    val parameters: List<String>,
    val activeParameter: Int,
    val documentation: String? = null,
)

data class ApiMemberDoc(val name: String, val kind: CompletionKind, val signature: String, val documentation: String?)

data class ApiTypeDoc(val name: String, val members: List<ApiMemberDoc>)

/** Result of applying a [Completion]: the new text and the caret offset in it. */
data class EditResult(val text: String, val caret: Int)

/** Applies this completion to [text]; the editor can use it directly or mirror it with its own buffer. */
fun Completion.applyTo(text: String): EditResult {
    val start = replaceStart.coerceIn(0, text.length)
    val end = replaceEnd.coerceIn(start, text.length)
    return EditResult(text.substring(0, start) + insertText + text.substring(end), start + cursorOffset.coerceIn(0, insertText.length))
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
