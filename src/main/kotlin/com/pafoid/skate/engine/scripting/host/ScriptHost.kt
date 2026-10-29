package com.pafoid.skate.engine.scripting.host

import com.pafoid.skate.engine.scripting.api.Script
import com.pafoid.skate.engine.scripting.completion.CompletionProvider

enum class ScriptLanguage { KOTLIN, TYPESCRIPT, LUA }

data class ScriptSource(val path: String, val text: String)

data class ScriptError(
    val path: String,
    val line: Int?,
    val column: Int?,
    val message: String,
    val cause: Throwable? = null,
) {
    override fun toString(): String = buildString {
        append(path)
        if (line != null) append(':').append(line)
        if (column != null) append(':').append(column)
        append(": ").append(message)
    }
}

/** Creates one [Script] instance per attached entity. Hosts adapt foreign languages to [Script]. */
interface ScriptFactory {
    fun instantiate(): Script
}

sealed interface ScriptLoadResult {
    data class Success(val factory: ScriptFactory) : ScriptLoadResult
    data class Failure(val errors: List<ScriptError>) : ScriptLoadResult
}

interface ScriptHost {
    val language: ScriptLanguage
    val fileExtensions: Set<String>

    /** Compiles (with caching) and returns a factory. Must never throw. */
    fun load(source: ScriptSource): ScriptLoadResult

    /** Recompiles ignoring caches for [source] (used by hot reload). */
    fun reload(source: ScriptSource): ScriptLoadResult = load(source)

    /** Code-completion for this language (SCR-06); null when the host offers none. */
    val completionProvider: CompletionProvider? get() = null

    fun close() {}
}

class ScriptHostRegistry {
    private val hosts = mutableMapOf<String, ScriptHost>()

    fun register(host: ScriptHost) {
        host.fileExtensions.forEach { hosts[it.lowercase().removePrefix(".")] = host }
    }

    fun hostFor(path: String): ScriptHost? = hosts[path.substringAfterLast('.', "").lowercase()]

    fun completionProviderFor(path: String): CompletionProvider? = hostFor(path)?.completionProvider

    fun closeAll() = hosts.values.distinct().forEach { it.close() }
}
