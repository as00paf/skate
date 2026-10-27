package com.pafoid.skate.engine.scripting.host

import com.pafoid.skate.engine.scripting.api.Script
import com.pafoid.skate.engine.scripting.api.SkateScript
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.compilationCache
import kotlinx.coroutines.runBlocking

/**
 * Compiles `.kts` files whose body registers lifecycle callbacks on [SkateScript].
 * Compiled scripts are cached in memory by content hash and, when [cacheDir] is set, on disk.
 */
class KotlinScriptHost(private val cacheDir: File? = null) : ScriptHost {

    override val language = ScriptLanguage.KOTLIN
    override val fileExtensions = setOf("kts")

    private val compilationConfiguration = ScriptCompilationConfiguration {
        baseClass(SkateScript::class)
        defaultImports(
            "com.pafoid.skate.engine.scripting.api.*",
            "org.joml.Vector3f",
        )
        jvm {
            dependenciesFromClassContext(SkateScript::class, wholeClasspath = true)
        }
    }

    private val host = BasicJvmScriptingHost(
        compiler = JvmScriptCompiler(
            ScriptingHostConfiguration {
                if (cacheDir != null) {
                    jvm {
                        compilationCache(CompiledScriptJarsCache { script, _ ->
                            File(cacheDir.apply { mkdirs() }, "${cacheKey(script.text)}.jar")
                        })
                    }
                }
            }
        ),
        evaluator = BasicJvmScriptEvaluator(),
    )

    private val memoryCache = ConcurrentHashMap<String, CompiledScript>()

    /** Number of real compilations performed (cache hits excluded). */
    @Volatile
    var compilations = 0
        private set

    override fun load(source: ScriptSource): ScriptLoadResult = compile(source, useCache = true)

    override fun reload(source: ScriptSource): ScriptLoadResult = compile(source, useCache = false)

    private fun compile(source: ScriptSource, useCache: Boolean): ScriptLoadResult {
        val key = cacheKey(source.text)
        if (useCache) memoryCache[key]?.let { return ScriptLoadResult.Success(factoryFor(source.path, it)) }

        return try {
            compilations++
            val code = source.text.toScriptSource(source.path)
            val result = runBlocking { host.compiler(code, compilationConfiguration) }
            val compiled = result.valueOrNull()
            if (compiled == null) {
                ScriptLoadResult.Failure(errorsOf(source.path, result.reports))
            } else {
                memoryCache[key] = compiled
                ScriptLoadResult.Success(factoryFor(source.path, compiled))
            }
        } catch (t: Throwable) {
            ScriptLoadResult.Failure(listOf(ScriptError(source.path, null, null, t.message ?: t.toString(), t)))
        }
    }

    private fun factoryFor(path: String, compiled: CompiledScript) = object : ScriptFactory {
        override fun instantiate(): Script {
            val result = runBlocking { host.evaluator(compiled, ScriptEvaluationConfiguration {}) }
            val evaluation = result.valueOrNull()
                ?: throw ScriptInstantiationException(errorsOf(path, result.reports))
            when (val value = evaluation.returnValue) {
                is ResultValue.Error -> throw ScriptInstantiationException(
                    listOf(ScriptError(path, null, null, value.error.message ?: value.error.toString(), value.error))
                )
                else -> return value.scriptInstance as? SkateScript
                    ?: throw ScriptInstantiationException(listOf(ScriptError(path, null, null, "Script is not a SkateScript")))
            }
        }
    }

    private fun errorsOf(path: String, reports: List<ScriptDiagnostic>): List<ScriptError> {
        val errors = reports.filter { it.severity >= ScriptDiagnostic.Severity.ERROR }
        return errors.map {
            ScriptError(path, it.location?.start?.line, it.location?.start?.col, it.message, it.exception)
        }.ifEmpty { listOf(ScriptError(path, null, null, "Script compilation failed")) }
    }

    private fun cacheKey(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    override fun close() = memoryCache.clear()
}

class ScriptInstantiationException(val errors: List<ScriptError>) : RuntimeException(errors.joinToString("; "))
