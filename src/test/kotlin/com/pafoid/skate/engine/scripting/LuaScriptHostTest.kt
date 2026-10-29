package com.pafoid.skate.engine.scripting

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.scripting.host.LuaScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptError
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.host.ScriptLoadResult
import com.pafoid.skate.engine.scripting.host.ScriptSource
import com.pafoid.skate.engine.scripting.runtime.ScriptRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LuaScriptHostTest {

    private class Fixture(path: String, var text: String, props: Map<String, String> = emptyMap()) {
        val transform = Transform()
        val logs = mutableListOf<String>()
        val errors = mutableListOf<ScriptError>()
        val component = ScriptComponent(path, props.toMutableMap()).also {
            GameObject("hero").apply { addComponent(transform); addComponent(it) }
        }
        val runtime = ScriptRuntime(
            ScriptHostRegistry().apply { register(LuaScriptHost()) },
            sceneRoot = { Scene() },
            onError = { errors += it },
            logSink = { _, m -> logs += m },
            readSource = { text },
            reloadCheckInterval = 0f,
        )

        fun tick(dt: Float = 0.5f) = runtime.update(listOf(component), dt)
    }

    @Test
    fun LuaHost_RunsLifecycleAgainstContext() {
        val f = Fixture(
            "mover.lua",
            """
            local total = 0
            function onCreate(ctx)
              ctx.log.info("hello " .. ctx.entity.name)
              ctx.state.set("seen", 1)
            end
            function onUpdate(ctx, dt)
              total = total + dt
              ctx.entity.transform.position.x = total * tonumber(ctx.properties.speed)
              ctx.entity.transform.position.y = ctx.state.get("seen") + ctx.time.deltaTime
            end
            """.trimIndent(),
            mapOf("speed" to "4"),
        )

        f.tick()

        assertEquals(emptyList(), f.errors)
        assertEquals(2f, f.transform.translation.x, 1e-4f)
        assertEquals(1.5f, f.transform.translation.y, 1e-4f)
        assertTrue(f.logs.any { it.contains("hello hero") })
    }

    @Test
    fun LuaHost_SyntaxErrorHasFileAndLine() {
        val result = LuaScriptHost().load(ScriptSource("scripts/bad.lua", "local a = 1\nlocal = = 2"))

        assertIs<ScriptLoadResult.Failure>(result)
        assertEquals(2, result.errors.single().line)
        assertEquals("scripts/bad.lua", result.errors.single().path)
    }

    @Test
    fun LuaHost_RuntimeErrorReportedWithLine() {
        val f = Fixture("boom.lua", "function onUpdate(ctx, dt)\n  error('kaboom')\nend")

        f.tick()

        assertEquals(2, f.errors.single().line)
        assertTrue(f.errors.single().message.contains("kaboom"))
    }

    @Test
    fun LuaHost_SandboxHasNoHostOrIoAccess() {
        val f = Fixture(
            "evil.lua",
            "function onCreate(ctx)\n  ctx.log.info(tostring(io) .. tostring(os) .. tostring(luajava) .. tostring(require) .. tostring(debug))\nend",
        )

        f.tick()

        assertEquals(emptyList(), f.errors)
        assertTrue(f.logs.single().contains("nilnilnilnilnil"), f.logs.toString())
    }

    @Test
    fun LuaHost_HotReloadSwapsScript() {
        val f = Fixture("hot.lua", "function onUpdate(ctx, dt) ctx.entity.transform.position.x = 1 end")
        f.tick()
        f.text = "function onUpdate(ctx, dt) ctx.entity.transform.position.x = 2 end"
        f.runtime.update(listOf(f.component), 0.5f)
        f.tick()

        assertEquals(2f, f.transform.translation.x, 1e-4f)
    }
}
