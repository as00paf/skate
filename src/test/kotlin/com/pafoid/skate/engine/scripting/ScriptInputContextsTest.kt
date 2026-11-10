package com.pafoid.skate.engine.scripting

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.input.ContextBlocking
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.InputContextStack
import com.pafoid.skate.engine.input.InputReader
import com.pafoid.skate.engine.scripting.api.Script
import com.pafoid.skate.engine.scripting.api.ScriptContext
import com.pafoid.skate.engine.scripting.host.ScriptError
import com.pafoid.skate.engine.scripting.host.ScriptFactory
import com.pafoid.skate.engine.scripting.host.ScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.host.ScriptLanguage
import com.pafoid.skate.engine.scripting.host.ScriptLoadResult
import com.pafoid.skate.engine.scripting.host.ScriptSource
import com.pafoid.skate.engine.scripting.runtime.NoOpScriptInput
import com.pafoid.skate.engine.scripting.runtime.ReaderScriptInput
import com.pafoid.skate.engine.scripting.runtime.ScriptInputScope
import com.pafoid.skate.engine.scripting.runtime.ScriptRuntime
import com.pafoid.skate.engine.scripting.runtime.ScriptServices
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** INP-10 step 7: scripts read actions and hold input contexts that never outlive the script. */
class ScriptInputContextsTest {

    private val vehicle = InputContext("vehicle")

    /** Source text drives the script: it pushes "vehicle" on create; "BOOM" also throws on update. */
    private class ContextHost : ScriptHost {
        override val language = ScriptLanguage.LUA
        override val fileExtensions = setOf("fake")
        override fun load(source: ScriptSource): ScriptLoadResult = ScriptLoadResult.Success(object : ScriptFactory {
            override fun instantiate() = object : Script {
                override fun onCreate(ctx: ScriptContext) {
                    ctx.input.pushContext("vehicle", 10, "consume")
                }
                override fun onUpdate(ctx: ScriptContext, dt: Float) {
                    if (source.text == "BOOM") error("boom")
                }
            }
        })
    }

    private class Fixture {
        val stack = InputContextStack.withDefaults()
        var text = "v1"
        val errors = mutableListOf<ScriptError>()
        val component = ScriptComponent("s.fake").also {
            GameObject("e").apply { addComponent(Transform()); addComponent(it) }
        }
        val runtime = ScriptRuntime(
            ScriptHostRegistry().apply { register(ContextHost()) },
            services = ScriptServices(inputContexts = stack),
            sceneRoot = { Scene() },
            onError = { errors += it },
            readSource = { text },
            reloadCheckInterval = 0f,
        )

        fun tick() = runtime.update(listOf(component), 0.001f)
        fun held(context: InputContext) = stack.entries().count { it.context == context }
    }

    @Test
    fun ScriptContexts_PushedContextIsPoppedWhenPlayStops() {
        val f = Fixture()
        f.tick()
        assertEquals(1, f.held(vehicle))
        assertEquals("script:s.fake", f.stack.entries().first { it.context == vehicle }.owner)
        f.runtime.stop()
        assertEquals(0, f.held(vehicle))
        assertTrue(f.stack.isActive(InputContext.GAMEPLAY))
    }

    @Test
    fun ScriptContexts_HotReloadReleasesTheOldScriptsContexts() {
        val f = Fixture()
        f.tick()
        f.text = "v2"
        f.tick()
        assertEquals(1, f.held(vehicle), "old push popped, new onCreate pushed once")
    }

    @Test
    fun ScriptContexts_RemovedComponentReleasesContexts() {
        val f = Fixture()
        f.tick()
        f.runtime.update(emptyList(), 0.001f)
        assertEquals(0, f.held(vehicle))
    }

    @Test
    fun ScriptContexts_FailingScriptReleasesContexts() {
        val f = Fixture()
        f.text = "BOOM"
        f.tick()
        assertTrue(f.errors.isNotEmpty())
        assertEquals(0, f.held(vehicle))
    }

    @Test
    fun LuaHost_PushesAndPopsContextsWithOptionalArguments() {
        val stack = InputContextStack()
        val logs = mutableListOf<String>()
        val errors = mutableListOf<ScriptError>()
        val component = ScriptComponent("ctx.lua").also { GameObject("e").apply { addComponent(Transform()); addComponent(it) } }
        val runtime = ScriptRuntime(
            ScriptHostRegistry().apply { register(com.pafoid.skate.engine.scripting.host.LuaScriptHost()) },
            services = ScriptServices(inputContexts = stack),
            sceneRoot = { Scene() },
            onError = { errors += it },
            logSink = { _, m -> logs += m },
            readSource = {
                """
                function onCreate(ctx)
                  local a = ctx.input.pushContext("vehicle")
                  local b = ctx.input.pushContext("menu", 50, "all")
                  ctx.log.info("popped " .. tostring(ctx.input.popContext(b)) .. " " .. tostring(ctx.input.popContext(b)))
                  ctx.log.info("down " .. tostring(ctx.input.isActionDown("fire")) .. " " .. tostring(ctx.input.wasActionPressed("fire")))
                end
                """.trimIndent()
            },
        )
        runtime.update(listOf(component), 0.01f)
        assertEquals(emptyList(), errors)
        assertEquals(listOf(vehicle), stack.entries().map { it.context })
        assertEquals(ContextBlocking.CONSUME_BOUND, stack.entries().single().blocking)
        assertTrue(logs.any { it.contains("popped true false") }, "$logs")
        assertTrue(logs.any { it.contains("down false false") }, "$logs")
        runtime.stop()
        assertTrue(stack.entries().isEmpty())
    }

    @Test
    fun ScriptInputScope_PushPopAndBlockingNames() {
        val stack = InputContextStack()
        val scope = ScriptInputScope(NoOpScriptInput, stack, "script:test")
        val h = scope.pushContext("menu", 100, "ALL")
        assertEquals(ContextBlocking.ALL, stack.entries().single().blocking)
        assertEquals(1, scope.heldCount)
        assertTrue(scope.popContext(h))
        assertFalse(scope.popContext(h))
        assertFalse(scope.popContext(999))
        assertEquals(ContextBlocking.NONE, ScriptInputScope.parseBlocking(" none "))
        assertEquals(ContextBlocking.CONSUME_BOUND, ScriptInputScope.parseBlocking("consume"))
        assertFailsWith<IllegalArgumentException> { scope.pushContext("menu", 0, "modal") }
        assertFailsWith<IllegalArgumentException> { scope.pushContext(" ", 0, "none") }
    }

    @Test
    fun ScriptInputScope_WithoutStackContextsAreUnsupported() {
        val scope = ScriptInputScope(NoOpScriptInput, null, "script:test")
        assertEquals(-1, scope.pushContext("menu", 0, "all"))
        assertFalse(scope.popContext(-1))
    }

    @Test
    fun ReaderScriptInput_ReadsActionsThroughTheReader() {
        val reader = mockk<InputReader>()
        every { reader.isActionDown("fire") } returns true
        every { reader.wasActionPressed("fire") } returns true
        val input = ReaderScriptInput(reader)
        assertTrue(input.isActionDown("fire"))
        assertTrue(input.wasActionPressed("fire"))
        assertEquals(-1, input.pushContext("menu", 0, "all"), "contexts are per script, via ScriptInputScope")
    }
}
