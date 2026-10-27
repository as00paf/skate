package com.pafoid.skate.engine.scripting

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.scripting.api.Script
import com.pafoid.skate.engine.scripting.api.ScriptContext
import com.pafoid.skate.engine.scripting.host.ScriptFactory
import com.pafoid.skate.engine.scripting.host.ScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.host.ScriptLanguage
import com.pafoid.skate.engine.scripting.host.ScriptLoadResult
import com.pafoid.skate.engine.scripting.host.ScriptSource
import com.pafoid.skate.engine.scripting.runtime.ScriptRuntime
import com.pafoid.skate.engine.scripting.host.ScriptError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptRuntimeTest {

    private class FakeHost(val scripts: Map<String, () -> Script>) : ScriptHost {
        override val language = ScriptLanguage.LUA
        override val fileExtensions = setOf("fake")
        override fun load(source: ScriptSource): ScriptLoadResult {
            val make = scripts[source.path]
                ?: return ScriptLoadResult.Failure(listOf(ScriptError(source.path, 3, 1, "bad script")))
            return ScriptLoadResult.Success(object : ScriptFactory {
                override fun instantiate() = make()
            })
        }
    }

    private class Recorder(val name: String, val calls: MutableList<String>, val failOnUpdate: Boolean = false) : Script {
        override fun onCreate(ctx: ScriptContext) { calls += "$name.create" }
        override fun onUpdate(ctx: ScriptContext, dt: Float) {
            calls += "$name.update"
            if (failOnUpdate) error("boom")
        }
        override fun onFixedUpdate(ctx: ScriptContext, dt: Float) { calls += "$name.fixed" }
        override fun onDestroy(ctx: ScriptContext) { calls += "$name.destroy" }
    }

    private fun entity(name: String, path: String): ScriptComponent {
        val go = GameObject(name)
        go.addComponent(Transform())
        val component = ScriptComponent(path)
        go.addComponent(component)
        return component
    }

    private fun runtime(host: ScriptHost, errors: MutableList<ScriptError> = mutableListOf()): ScriptRuntime {
        val registry = ScriptHostRegistry().apply { register(host) }
        return ScriptRuntime(registry, sceneRoot = { Scene() }, onError = { errors += it }, readSource = { "src" })
    }

    @Test
    fun ScriptRuntime_DispatchesLifecycleInOrder() {
        val calls = mutableListOf<String>()
        val rt = runtime(FakeHost(mapOf("a.fake" to { Recorder("a", calls) })))
        val component = entity("e", "a.fake")

        rt.update(listOf(component), 1f / 60f)
        rt.stop()

        assertEquals(listOf("a.create", "a.fixed", "a.update", "a.destroy"), calls)
    }

    @Test
    fun ScriptRuntime_FailingScriptDoesNotAffectOthers() {
        val calls = mutableListOf<String>()
        val errors = mutableListOf<ScriptError>()
        val rt = runtime(
            FakeHost(mapOf("bad.fake" to { Recorder("bad", calls, failOnUpdate = true) }, "ok.fake" to { Recorder("ok", calls) })),
            errors,
        )
        val comps = listOf(entity("b", "bad.fake"), entity("o", "ok.fake"))

        rt.update(comps, 0.001f)
        rt.update(comps, 0.001f)

        assertEquals(1, errors.size)
        assertEquals(1, calls.count { it == "bad.update" })
        assertEquals(2, calls.count { it == "ok.update" })
    }

    @Test
    fun ScriptRuntime_ReportsLoadErrorsWithFileAndLine() {
        val errors = mutableListOf<ScriptError>()
        val rt = runtime(FakeHost(emptyMap()), errors)

        rt.update(listOf(entity("e", "missing.fake")), 0.01f)

        assertEquals("missing.fake:3:1: bad script", errors.single().toString())
    }

    @Test
    fun ScriptRuntime_RemovedComponentIsDestroyed() {
        val calls = mutableListOf<String>()
        val rt = runtime(FakeHost(mapOf("a.fake" to { Recorder("a", calls) })))
        val component = entity("e", "a.fake")

        rt.update(listOf(component), 0.001f)
        rt.update(emptyList(), 0.001f)

        assertTrue("a.destroy" in calls)
    }
}
