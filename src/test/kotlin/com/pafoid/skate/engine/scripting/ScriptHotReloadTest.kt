package com.pafoid.skate.engine.scripting

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.scripting.api.Script
import com.pafoid.skate.engine.scripting.api.ScriptContext
import com.pafoid.skate.engine.scripting.host.ScriptError
import com.pafoid.skate.engine.scripting.host.ScriptFactory
import com.pafoid.skate.engine.scripting.host.ScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.host.ScriptLanguage
import com.pafoid.skate.engine.scripting.host.ScriptLoadResult
import com.pafoid.skate.engine.scripting.host.ScriptSource
import com.pafoid.skate.engine.scripting.runtime.ScriptRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptHotReloadTest {

    /** Source text is the script's version label; "BAD" fails to compile. */
    private class TextHost(val calls: MutableList<String>) : ScriptHost {
        override val language = ScriptLanguage.LUA
        override val fileExtensions = setOf("fake")
        override fun load(source: ScriptSource): ScriptLoadResult {
            if (source.text == "BAD") return ScriptLoadResult.Failure(listOf(ScriptError(source.path, 7, 2, "syntax")))
            return ScriptLoadResult.Success(object : ScriptFactory {
                override fun instantiate() = object : Script {
                    override fun onCreate(ctx: ScriptContext) {
                        val n = (ctx.state["count"] as Int?) ?: 0
                        ctx.state["count"] = n + 1
                        calls += "${source.text}.create#${ctx.reloadCount}/count=${n + 1}"
                    }
                    override fun onUpdate(ctx: ScriptContext, dt: Float) { calls += "${source.text}.update" }
                    override fun onDestroy(ctx: ScriptContext) { calls += "${source.text}.destroy" }
                }
            })
        }
    }

    private class Fixture {
        val calls = mutableListOf<String>()
        val errors = mutableListOf<ScriptError>()
        var text = "v1"
        val component = ScriptComponent("s.fake").also {
            GameObject("e").apply { addComponent(Transform()); addComponent(it) }
        }
        val runtime = ScriptRuntime(
            ScriptHostRegistry().apply { register(TextHost(calls)) },
            sceneRoot = { Scene() },
            onError = { errors += it },
            readSource = { text },
            reloadCheckInterval = 0f,
        )

        fun tick() = runtime.update(listOf(component), 0.001f)
    }

    @Test
    fun ScriptHotReload_AppliesChangedSourceKeepingState() {
        val f = Fixture()
        f.tick()
        f.text = "v2"
        f.tick()

        assertEquals(
            listOf("v1.create#0/count=1", "v1.update", "v1.destroy", "v2.create#1/count=2", "v2.update"),
            f.calls,
        )
    }

    @Test
    fun ScriptHotReload_CompileErrorKeepsOldScriptRunning() {
        val f = Fixture()
        f.tick()
        f.text = "BAD"
        f.tick()
        f.tick()

        assertEquals("s.fake:7:2: syntax", f.errors.single().toString())
        assertEquals(3, f.calls.count { it == "v1.update" })
        assertTrue(f.calls.none { it.endsWith("destroy") })
    }

    @Test
    fun ScriptHotReload_FailedScriptRecoversWhenFixed() {
        val f = Fixture()
        f.text = "BAD"
        f.tick()
        f.text = "v2"
        f.tick()

        assertEquals(1, f.errors.size)
        assertEquals(listOf("v2.create#1/count=1", "v2.update"), f.calls)
    }
}
