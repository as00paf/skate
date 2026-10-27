package com.pafoid.skate.engine.scripting.host

import com.pafoid.skate.engine.scripting.api.Script
import com.pafoid.skate.engine.scripting.api.ScriptContext
import com.pafoid.skate.engine.scripting.api.ScriptEntity
import com.pafoid.skate.engine.scripting.api.ScriptEvents
import com.pafoid.skate.engine.scripting.api.ScriptInput
import com.pafoid.skate.engine.scripting.api.ScriptLog
import com.pafoid.skate.engine.scripting.api.ScriptPhysics
import com.pafoid.skate.engine.scripting.api.ScriptRigidBody
import com.pafoid.skate.engine.scripting.api.ScriptScene
import com.pafoid.skate.engine.scripting.api.ScriptTime
import com.pafoid.skate.engine.scripting.api.ScriptTransform
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.joml.Vector3f
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs TypeScript (`.ts`) and JavaScript (`.js`) scripts on GraalJS. TypeScript is transpiled with the
 * bundled TypeScript compiler (syntax errors only, no type checking). A script declares top-level
 * `onCreate/onUpdate/onFixedUpdate/onDestroy` functions (exported or not) that receive an idiomatic
 * wrapper of [ScriptContext]; each instance runs in its own isolated JS context that can only reach the script API.
 */
class TypeScriptHost : ScriptHost {

    override val language = ScriptLanguage.TYPESCRIPT
    override val fileExtensions = setOf("ts", "js")

    private val engine: Engine by lazy {
        Engine.newBuilder().option("engine.WarnInterpreterOnly", "false").build()
    }

    private val hostAccess = buildHostAccess()
    private val transpilerDelegate = lazy { TypeScriptTranspiler(engine, hostAccess) }
    private val transpiler by transpilerDelegate
    private val preludeSource: Source by lazy {
        val text = javaClass.getResourceAsStream("/scripting/ts-prelude.js")!!.use { it.readBytes().decodeToString() }
        Source.newBuilder("js", text, "skate-prelude.js").buildLiteral()
    }
    private class Compiled(val source: Source, val lines: SourceMapLines?)

    private val cache = ConcurrentHashMap<String, Compiled>()

    /** Number of real transpilations/compilations performed (cache hits excluded). */
    @Volatile
    var compilations = 0
        private set

    override fun load(source: ScriptSource): ScriptLoadResult = compile(source, useCache = true)

    override fun reload(source: ScriptSource): ScriptLoadResult = compile(source, useCache = false)

    private fun compile(source: ScriptSource, useCache: Boolean): ScriptLoadResult {
        val key = source.path + "\u0000" + sha(source.text)
        return try {
            val compiled = cache[key]?.takeIf { useCache } ?: run {
                compilations++
                var js = source.text
                var lines: SourceMapLines? = null
                if (source.path.endsWith(".ts", ignoreCase = true)) {
                    when (val result = transpiler.transpile(source)) {
                        is TranspileResult.Failure -> return ScriptLoadResult.Failure(result.errors)
                        is TranspileResult.Success -> {
                            js = result.js
                            lines = SourceMapLines.parse(result.sourceMap)
                        }
                    }
                }
                Compiled(Source.newBuilder("js", js, source.path).buildLiteral(), lines).also { cache[key] = it }
            }
            ScriptLoadResult.Success(object : ScriptFactory {
                override fun instantiate(): Script = JsScript(source.path, newContext(), compiled.source, compiled.lines)
            })
        } catch (t: Throwable) {
            ScriptLoadResult.Failure(listOf(ScriptError(source.path, null, null, t.message ?: t.toString(), t)))
        }
    }

    private fun newContext(): Context = Context.newBuilder("js")
        .engine(engine)
        .allowHostAccess(hostAccess)
        .allowHostClassLookup { false }
        .allowIO(false)
        .allowCreateThread(false)
        .allowNativeAccess(false)
        .allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess.NONE)
        .build()
        .also { it.eval(preludeSource) }

    override fun close() {
        cache.clear()
        if (transpilerDelegate.isInitialized()) transpiler.close()
        engine.close()
    }

    private inner class JsScript(
        private val path: String,
        private val context: Context,
        private val source: Source,
        private val lines: SourceMapLines?,
    ) : Script {
        private var handlers: Map<String, Value?> = emptyMap()
        private var wrapped: Value? = null

        init {
            guard { context.eval(source) }
            val lookup = context.getBindings("js").getMember("__skateHandler")
            handlers = HANDLER_NAMES.associateWith { lookup.execute(it).takeUnless { v -> v.isNull } }
        }

        private fun wrapper(ctx: ScriptContext): Value =
            wrapped ?: context.getBindings("js").getMember("__skateWrap").execute(ctx).also { wrapped = it }

        private fun invoke(name: String, ctx: ScriptContext, vararg args: Any) {
            val handler = handlers[name] ?: return
            guard { handler.execute(wrapper(ctx), *args) }
        }

        override fun onCreate(ctx: ScriptContext) = invoke("onCreate", ctx)
        override fun onUpdate(ctx: ScriptContext, dt: Float) = invoke("onUpdate", ctx, dt)
        override fun onFixedUpdate(ctx: ScriptContext, dt: Float) = invoke("onFixedUpdate", ctx, dt)
        override fun onDestroy(ctx: ScriptContext) {
            try {
                invoke("onDestroy", ctx)
            } finally {
                context.close(true)
            }
        }

        private inline fun <T> guard(block: () -> T): T = try {
            block()
        } catch (e: PolyglotException) {
            if (e.isInternalError) throw e
            val jsLine = e.sourceLocation?.startLine
                ?: e.polyglotStackTrace.firstOrNull { it.isGuestFrame && it.sourceLocation != null }?.sourceLocation?.startLine
            throw ScriptExecutionException(e.message ?: "Script error", path, jsLine?.let { lines?.originalLine(it) ?: it })
        }
    }

    private companion object {
        val HANDLER_NAMES = listOf("onCreate", "onUpdate", "onFixedUpdate", "onDestroy")

        fun sha(text: String) =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Exactly the script API (plus JOML vectors and Map get/put); nothing else is reachable from JS. */
        fun buildHostAccess(): HostAccess {
            val builder = HostAccess.newBuilder()
                .allowMapAccess(true)
                .allowListAccess(true)
                .allowIterableAccess(true)
                .allowArrayAccess(true)
                .allowAllImplementations(true)
                .allowAccessInheritance(true)
            listOf(
                ScriptContext::class, ScriptEntity::class, ScriptTransform::class, ScriptRigidBody::class,
                ScriptScene::class, ScriptInput::class, ScriptPhysics::class, ScriptEvents::class,
                ScriptTime::class, ScriptLog::class, Vector3f::class,
            ).forEach { type ->
                type.java.methods.filter { it.declaringClass != Any::class.java }.forEach { builder.allowAccess(it) }
            }
            Vector3f::class.java.fields.forEach { builder.allowAccess(it) }
            builder.allowAccess(Map::class.java.getMethod("get", Any::class.java))
            builder.allowAccess(Map::class.java.getMethod("put", Any::class.java, Any::class.java))
            return builder.build()
        }
    }
}

/** Script failure carrying the script file and line so [com.pafoid.skate.engine.scripting.runtime.ScriptRuntime] can report it. */
class ScriptExecutionException(message: String, val file: String, val line: Int?) : RuntimeException(message) {
    init {
        stackTrace = arrayOf(StackTraceElement("script", "run", java.io.File(file).name, line ?: -1))
    }
}

sealed interface TranspileResult {
    data class Success(val js: String, val sourceMap: String) : TranspileResult
    data class Failure(val errors: List<ScriptError>) : TranspileResult
}

/** Wraps the TypeScript compiler (webjar) in a private JS context; calls are serialized. */
class TypeScriptTranspiler(engine: Engine, hostAccess: HostAccess) : AutoCloseable {
    private val context: Context = Context.newBuilder("js")
        .engine(engine)
        .allowHostAccess(hostAccess)
        .allowHostClassLookup { false }
        .allowIO(false)
        .build()

    private val transpileFn: Value by lazy {
        val stream = javaClass.getResourceAsStream(TS_RESOURCE) ?: error("TypeScript compiler not found: $TS_RESOURCE")
        val text = stream.use { it.readBytes().decodeToString() }
        context.eval("js", "var module = { exports: {} }; var exports = module.exports;")
        context.eval(Source.newBuilder("js", text, "typescript.js").buildLiteral())
        context.eval("js", "if (typeof ts === 'undefined') { globalThis.ts = module.exports; }")
        context.eval(
            "js",
            """
            (function (text, fileName) {
                const out = ts.transpileModule(text, {
                    fileName: fileName,
                    reportDiagnostics: true,
                    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, sourceMap: true },
                });
                const errors = (out.diagnostics || []).filter(d => d.category === ts.DiagnosticCategory.Error).map(d => {
                    let line = null, col = null;
                    if (d.file && d.start !== undefined) {
                        const p = d.file.getLineAndCharacterOfPosition(d.start);
                        line = p.line + 1; col = p.character + 1;
                    }
                    return { line: line, col: col, message: ts.flattenDiagnosticMessageText(d.messageText, '\n') };
                });
                return JSON.stringify({ js: out.outputText, map: out.sourceMapText || '', errors: errors });
            })
            """.trimIndent(),
        )
    }

    @Synchronized
    fun transpile(source: ScriptSource): TranspileResult {
        val json = transpileFn.execute(source.text, source.path).asString()
        val root = Json.parseToJsonElement(json) as JsonObject
        val errors = (root["errors"] as JsonArray).map {
            val o = it as JsonObject
            ScriptError(
                source.path,
                (o["line"] as? JsonPrimitive)?.content?.toIntOrNull(),
                (o["col"] as? JsonPrimitive)?.content?.toIntOrNull(),
                (o["message"] as JsonPrimitive).content,
            )
        }
        return if (errors.isEmpty()) {
            TranspileResult.Success((root["js"] as JsonPrimitive).content, (root["map"] as JsonPrimitive).content)
        } else TranspileResult.Failure(errors)
    }

    @Synchronized
    override fun close() = context.close(true)

    private companion object {
        const val TS_RESOURCE = "/META-INF/resources/webjars/typescript/5.6.3/lib/typescript.js"
    }
}

/** Maps generated (1-based) lines back to the original TypeScript lines using a v3 source map. */
class SourceMapLines private constructor(private val firstSourceLine: Map<Int, Int>) {

    fun originalLine(generatedLine: Int): Int? =
        (generatedLine downTo 1).firstNotNullOfOrNull { firstSourceLine[it] }

    companion object {
        private const val BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        fun parse(json: String): SourceMapLines? {
            if (json.isBlank()) return null
            val mappings = (Json.parseToJsonElement(json) as JsonObject)["mappings"]?.let { (it as JsonPrimitive).content }
                ?: return null
            val result = mutableMapOf<Int, Int>()
            var sourceLine = 0
            mappings.split(';').forEachIndexed { index, group ->
                var first = true
                group.split(',').filter { it.isNotEmpty() }.forEach { segment ->
                    val fields = decodeVlq(segment)
                    if (fields.size >= 4) {
                        sourceLine += fields[2]
                        if (first) result[index + 1] = sourceLine + 1
                        first = false
                    }
                }
            }
            return SourceMapLines(result)
        }

        private fun decodeVlq(segment: String): List<Int> {
            val values = mutableListOf<Int>()
            var shift = 0
            var value = 0
            for (c in segment) {
                val digit = BASE64.indexOf(c)
                value += (digit and 31) shl shift
                if (digit and 32 != 0) {
                    shift += 5
                } else {
                    values += if (value and 1 == 1) -(value shr 1) else value shr 1
                    value = 0
                    shift = 0
                }
            }
            return values
        }
    }
}
