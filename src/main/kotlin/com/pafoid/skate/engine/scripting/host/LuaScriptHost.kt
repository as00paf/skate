package com.pafoid.skate.engine.scripting.host

import com.pafoid.skate.engine.scripting.completion.ApiCompletionProvider
import com.pafoid.skate.engine.scripting.completion.CompletionProvider
import com.pafoid.skate.engine.scripting.completion.LanguageProfile
import com.pafoid.skate.engine.scripting.api.Script
import com.pafoid.skate.engine.scripting.api.ScriptContext
import com.pafoid.skate.engine.scripting.api.ScriptEntity
import org.joml.Vector3f
import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaBoolean
import org.luaj.vm2.LuaDouble
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JseMathLib
import java.security.MessageDigest

/**
 * Lua host on LuaJ. Scripts define global `onCreate/onUpdate/onFixedUpdate/onDestroy(ctx[, dt])` functions.
 * The Lua environment is sandboxed: no io, os, debug, luajava, require, dofile or loadfile, and no Java
 * objects are ever handed to scripts (only tables and closures), so reflection is unreachable.
 */
class LuaScriptHost : ScriptHost {
    override val language = ScriptLanguage.LUA
    override val fileExtensions = setOf("lua")

    @Volatile
    var compilations = 0
        private set

    private val cache = HashMap<String, String>()

    override val completionProvider: CompletionProvider by lazy { ApiCompletionProvider(LanguageProfile.LUA) }

    override fun load(source: ScriptSource): ScriptLoadResult = compile(source, useCache = true)

    override fun reload(source: ScriptSource): ScriptLoadResult = compile(source, useCache = false)

    private fun compile(source: ScriptSource, useCache: Boolean): ScriptLoadResult {
        val key = source.path + "\u0000" + sha(source.text)
        return try {
            synchronized(cache) {
                if (!useCache || key !in cache) {
                    compilations++
                    // Parse once to surface syntax errors now; instances re-parse into their own globals.
                    newGlobals().load(source.text, chunkName(source.path))
                    cache[key] = source.text
                }
            }
            ScriptLoadResult.Success(object : ScriptFactory {
                override fun instantiate(): Script = LuaScript(source.path, source.text)
            })
        } catch (e: LuaError) {
            ScriptLoadResult.Failure(listOf(toError(source.path, e)))
        } catch (t: Throwable) {
            ScriptLoadResult.Failure(listOf(ScriptError(source.path, null, null, t.message ?: t.toString(), t)))
        }
    }

    override fun close() = synchronized(cache) { cache.clear() }

    private class LuaScript(private val path: String, private val text: String) : Script {
        private val globals = newGlobals()
        private var wrapped: LuaTable? = null

        init {
            guard(path) { globals.load(text, chunkName(path)).call() }
        }

        private fun wrapper(ctx: ScriptContext): LuaTable = wrapped ?: LuaContextWrapper.wrap(ctx).also { wrapped = it }

        private fun invoke(name: String, ctx: ScriptContext, vararg args: Float) {
            val fn = globals.get(name)
            if (!fn.isfunction()) return
            guard(path) {
                when (args.size) {
                    0 -> fn.call(wrapper(ctx))
                    else -> fn.call(wrapper(ctx), LuaValue.valueOf(args[0].toDouble()))
                }
            }
        }

        override fun onCreate(ctx: ScriptContext) = invoke("onCreate", ctx)
        override fun onUpdate(ctx: ScriptContext, dt: Float) = invoke("onUpdate", ctx, dt)
        override fun onFixedUpdate(ctx: ScriptContext, dt: Float) = invoke("onFixedUpdate", ctx, dt)
        override fun onDestroy(ctx: ScriptContext) = invoke("onDestroy", ctx)
    }

    private companion object {
        fun chunkName(path: String) = java.io.File(path).name

        fun sha(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Only safe libraries; no io, os, debug, luajava, coroutine-free package loading. */
        fun newGlobals(): Globals {
            val g = Globals()
            g.load(BaseLib())
            g.load(PackageLib())
            g.load(Bit32Lib())
            g.load(TableLib())
            g.load(StringLib())
            g.load(JseMathLib())
            LoadState.install(g)
            LuaC.install(g)
            for (name in listOf("dofile", "loadfile", "require", "package", "collectgarbage")) g.set(name, LuaValue.NIL)
            return g
        }

        private val LOCATION = Regex("^(?:\\[string \"(.*?)\"]|(.*?)):(\\d+):?\\s*(.*)$", RegexOption.DOT_MATCHES_ALL)

        fun toError(path: String, e: LuaError): ScriptError {
            val raw = e.message ?: "Lua error"
            val m = LOCATION.find(raw.lineSequence().first())
            return if (m != null) {
                ScriptError(path, m.groupValues[3].toInt(), null, m.groupValues[4].ifBlank { raw })
            } else ScriptError(path, null, null, raw)
        }

        inline fun <T> guard(path: String, block: () -> T): T = try {
            block()
        } catch (e: LuaError) {
            val err = toError(path, e)
            throw ScriptExecutionException(err.message, path, err.line)
        }
    }
}

/** Builds the Lua-side `ctx` table out of closures and plain tables; no Java object is exposed to Lua. */
internal object LuaContextWrapper {
    fun wrap(c: ScriptContext): LuaTable {
        val ctx = LuaTable()
        ctx.set("entity", entity(c.entity))
        ctx.set("scene", LuaTable().apply {
            fn("find") { a -> c.scene.find(a.checkjstring(1))?.let { entity(it) } ?: LuaValue.NIL }
            fn("findAll") { a -> list(c.scene.findAll(a.checkjstring(1)).map { entity(it) }) }
        })
        ctx.set("input", LuaTable().apply {
            fn("isKeyDown") { a -> bool(c.input.isKeyDown(a.checkint(1))) }
            fn("isKeyPressed") { a -> bool(c.input.isKeyPressed(a.checkint(1))) }
            fn("isKeyReleased") { a -> bool(c.input.isKeyReleased(a.checkint(1))) }
            fn("isMouseButtonDown") { a -> bool(c.input.isMouseButtonDown(a.checkint(1))) }
            fn("action") { a -> num(c.input.action(a.checkjstring(1)).toDouble()) }
        })
        ctx.set("physics", LuaTable().apply {
            set("gravity", vectorProxy(c.physics.gravity))
            fn("raycast") { a ->
                c.physics.raycast(toVector(a.arg(1)), toVector(a.arg(2)))?.let { num(it.toDouble()) } ?: LuaValue.NIL
            }
        })
        ctx.set("events", LuaTable().apply {
            fn("on") { a ->
                val name = a.checkjstring(1)
                val handler = a.checkfunction(2)
                c.events.on(name) { payload -> handler.call(toLua(payload)) }
                LuaValue.NIL
            }
            fn("emit") { a -> c.events.emit(a.checkjstring(1), fromLua(a.arg(2))); LuaValue.NIL }
        })
        ctx.set("time", object : LuaTable() {
            override fun get(key: LuaValue): LuaValue = when (key.tojstring()) {
                "deltaTime" -> num(c.time.deltaTime.toDouble())
                "fixedDeltaTime" -> num(c.time.fixedDeltaTime.toDouble())
                "elapsed" -> num(c.time.elapsed.toDouble())
                else -> super.get(key)
            }
        })
        ctx.set("log", LuaTable().apply {
            fn("info") { a -> c.log.info(a.tojstring(1)); LuaValue.NIL }
            fn("warn") { a -> c.log.warn(a.tojstring(1)); LuaValue.NIL }
            fn("error") { a -> c.log.error(a.tojstring(1)); LuaValue.NIL }
        })
        ctx.set("properties", LuaTable().apply { c.properties.forEach { (k, v) -> set(k, LuaValue.valueOf(v)) } })
        ctx.set("state", LuaTable().apply {
            fn("get") { a -> toLua(c.state[a.checkjstring(1)]) }
            fn("set") { a -> c.state[a.checkjstring(1)] = fromLua(a.arg(2)); LuaValue.NIL }
        })
        ctx.set("reloadCount", LuaValue.valueOf(c.reloadCount))
        return ctx
    }

    private fun entity(e: ScriptEntity): LuaTable = LuaTable().apply {
        set("id", LuaValue.valueOf(e.id))
        set("transform", LuaTable().apply {
            set("position", vectorProxy(e.transform.position))
            set("rotation", vectorProxy(e.transform.rotation))
            set("scale", vectorProxy(e.transform.scale))
        })
        set("name", LuaValue.valueOf(e.name))
        fn("getName") { LuaValue.valueOf(e.name) }
        fn("setName") { a -> e.name = a.checkjstring(1); LuaValue.NIL }
        fn("hasComponent") { a -> bool(e.component(a.checkjstring(1)) != null) }
        fn("getComponent") { a ->
            val name = a.checkjstring(1)
            val comp = e.component(name)
            if (comp is com.pafoid.skate.engine.scripting.api.ScriptRigidBody) rigidBody(comp) else LuaValue.NIL
        }
    }

    private fun rigidBody(b: com.pafoid.skate.engine.scripting.api.ScriptRigidBody): LuaTable = object : LuaTable() {
        override fun get(key: LuaValue): LuaValue = when (key.tojstring()) {
            "mass" -> num(b.mass.toDouble())
            "linearVelocity" -> vectorProxy(b.linearVelocity) { b.linearVelocity = it }
            "angularVelocity" -> vectorProxy(b.angularVelocity) { b.angularVelocity = it }
            else -> super.get(key)
        }

        override fun set(key: LuaValue, value: LuaValue) {
            when (key.tojstring()) {
                "mass" -> b.mass = value.checkdouble().toFloat()
                "linearVelocity" -> b.linearVelocity = toVector(value)
                "angularVelocity" -> b.angularVelocity = toVector(value)
                else -> super.set(key, value)
            }
        }
    }

    /** Table whose x/y/z read and write the vector; [commit] is called with the vector after each write. */
    private fun vectorProxy(v: Vector3f, commit: ((Vector3f) -> Unit)? = null): LuaTable = object : LuaTable() {
        override fun get(key: LuaValue): LuaValue = when (key.tojstring()) {
            "x" -> num(v.x.toDouble())
            "y" -> num(v.y.toDouble())
            "z" -> num(v.z.toDouble())
            else -> super.get(key)
        }

        override fun set(key: LuaValue, value: LuaValue) {
            when (key.tojstring()) {
                "x" -> v.x = value.checkdouble().toFloat()
                "y" -> v.y = value.checkdouble().toFloat()
                "z" -> v.z = value.checkdouble().toFloat()
                else -> return super.set(key, value)
            }
            commit?.invoke(v)
        }
    }

    private fun toVector(v: LuaValue): Vector3f =
        Vector3f(v.get("x").checkdouble().toFloat(), v.get("y").checkdouble().toFloat(), v.get("z").checkdouble().toFloat())

    private fun list(items: List<LuaValue>): LuaTable = LuaTable().apply { items.forEachIndexed { i, v -> set(i + 1, v) } }

    private fun bool(b: Boolean): LuaValue = LuaBoolean.valueOf(b)
    private fun num(d: Double): LuaValue = LuaDouble.valueOf(d)

    private fun LuaTable.fn(name: String, body: (Varargs) -> LuaValue) {
        set(name, object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs = body(args)
        })
    }

    private fun toLua(v: Any?): LuaValue = when (v) {
        null -> LuaValue.NIL
        is Boolean -> bool(v)
        is Int -> LuaValue.valueOf(v)
        is Number -> num(v.toDouble())
        else -> LuaValue.valueOf(v.toString())
    }

    private fun fromLua(v: LuaValue): Any? = when {
        v.isnil() -> null
        v.isboolean() -> v.toboolean()
        v.isint() -> v.toint()
        v.isnumber() -> v.todouble()
        else -> v.tojstring()
    }
}
