package com.pafoid.skate.engine.scripting.runtime

import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.scripting.api.Script
import com.pafoid.skate.engine.scripting.api.ScriptContext
import com.pafoid.skate.engine.scripting.api.ScriptEvents
import com.pafoid.skate.engine.scripting.api.ScriptLog
import com.pafoid.skate.engine.scripting.host.ScriptError
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.host.ScriptInstantiationException
import com.pafoid.skate.engine.scripting.host.ScriptLoadResult
import com.pafoid.skate.engine.scripting.host.ScriptSource
import java.io.File

/** Published when a script fails to compile or throws. The editor console shows `path:line`. */
class ScriptErrorEvent(val error: ScriptError) : Event("ScriptError")

/**
 * Language-blind lifecycle driver: loads scripts through [hosts], creates them once, then
 * dispatches fixed updates and updates. A failing script is disabled and reported; others keep running.
 */
class ScriptRuntime(
    private val hosts: ScriptHostRegistry,
    private val services: ScriptServices = ScriptServices(),
    private val sceneRoot: () -> GameObject,
    private val onError: (ScriptError) -> Unit = {},
    private val logSink: (LogLevel, String) -> Unit = { _, _ -> },
    private val readSource: (String) -> String? = { path -> File(path).takeIf { it.isFile }?.readText() },
    val fixedDeltaTime: Float = 1f / 60f,
) {
    private class Instance(val component: ScriptComponent) {
        var script: Script? = null
        var context: ScriptContext? = null
        var failed = false
        val live get() = script != null && !failed
    }

    private val instances = LinkedHashMap<ScriptComponent, Instance>()
    private val eventBus = ScriptEventBus()
    private val time = ScriptTimeImpl(fixedDeltaTime = fixedDeltaTime)
    private var accumulator = 0f

    fun update(components: List<ScriptComponent>, dt: Float) {
        dropRemoved(components)
        time.deltaTime = dt
        time.elapsed += dt

        val active = components.filter { it.enabled && it.path.isNotBlank() && it.gameObject != null }
        active.forEach { component ->
            val instance = instances.getOrPut(component) { Instance(component) }
            if (instance.script == null && !instance.failed) create(instance)
        }

        accumulator = (accumulator + dt).coerceAtMost(fixedDeltaTime * MAX_FIXED_STEPS)
        while (accumulator >= fixedDeltaTime) {
            accumulator -= fixedDeltaTime
            active.forEach { call(instances[it]) { script, ctx -> script.onFixedUpdate(ctx, fixedDeltaTime) } }
        }

        active.forEach { call(instances[it]) { script, ctx -> script.onUpdate(ctx, dt) } }
    }

    /** Destroys every script (play stopped); reverse creation order. */
    fun stop() {
        instances.values.reversed().forEach { destroy(it) }
        instances.clear()
        eventBus.clear()
        accumulator = 0f
        time.elapsed = 0f
    }

    private fun dropRemoved(components: List<ScriptComponent>) {
        val present = components.toHashSet()
        instances.keys.filter { it !in present }.reversed().forEach { key ->
            instances.remove(key)?.let { destroy(it) }
        }
    }

    private fun create(instance: Instance) {
        val component = instance.component
        val path = component.path
        val host = hosts.hostFor(path)
        if (host == null) return fail(instance, ScriptError(path, null, null, "No script host for '$path'"))
        val text = readSource(path)
            ?: return fail(instance, ScriptError(path, null, null, "Script file not found"))

        when (val result = host.load(ScriptSource(path, text))) {
            is ScriptLoadResult.Failure -> return fail(instance, result.errors)
            is ScriptLoadResult.Success -> try {
                instance.script = result.factory.instantiate()
            } catch (e: ScriptInstantiationException) {
                return fail(instance, e.errors)
            } catch (t: Throwable) {
                return fail(instance, errorFrom(path, t))
            }
        }

        instance.context = buildContext(component)
        call(instance) { script, ctx -> script.onCreate(ctx) }
    }

    private fun destroy(instance: Instance) {
        if (instance.live) call(instance) { script, ctx -> script.onDestroy(ctx) }
        eventBus.removeOwner(instance)
        instance.script = null
    }

    private fun call(instance: Instance?, block: (Script, ScriptContext) -> Unit) {
        if (instance == null || !instance.live) return
        val script = instance.script ?: return
        val ctx = instance.context ?: return
        try {
            block(script, ctx)
        } catch (t: Throwable) {
            fail(instance, errorFrom(instance.component.path, t))
        }
    }

    private fun fail(instance: Instance, error: ScriptError) = fail(instance, listOf(error))

    private fun fail(instance: Instance, errors: List<ScriptError>) {
        instance.failed = true
        errors.forEach {
            logSink(LogLevel.ERROR, it.toString())
            onError(it)
        }
    }

    private fun errorFrom(path: String, t: Throwable): ScriptError {
        val fileName = File(path).name
        val line = generateSequence(t) { it.cause }
            .flatMap { it.stackTrace.asSequence() }
            .firstOrNull { it.fileName == fileName }?.lineNumber?.takeIf { it > 0 }
        return ScriptError(path, line, null, t.message ?: t.javaClass.simpleName, t)
    }

    private fun buildContext(component: ScriptComponent): ScriptContext {
        val entity = ScriptEntityImpl(component.gameObject!!)
        val path = component.path
        val owner = instances[component]!!
        val log = object : ScriptLog {
            override fun info(message: String) = logSink(LogLevel.INFO, "[$path] $message")
            override fun warn(message: String) = logSink(LogLevel.WARN, "[$path] $message")
            override fun error(message: String) = logSink(LogLevel.ERROR, "[$path] $message")
        }
        val events = object : ScriptEvents {
            override fun on(name: String, handler: (Any?) -> Unit) = eventBus.on(owner, name, handler)
            override fun emit(name: String, payload: Any?) =
                eventBus.emit(name, payload) { fail(owner, errorFrom(path, it)) }
        }
        return ScriptContextImpl(
            entity = entity,
            scene = ScriptSceneImpl(sceneRoot),
            input = services.input,
            physics = services.physics,
            events = events,
            time = time,
            log = log,
            properties = component.properties.toMap(),
        )
    }

    private companion object {
        const val MAX_FIXED_STEPS = 5
    }
}
