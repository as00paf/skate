package com.pafoid.skate.engine.scripting

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.scripting.host.KotlinScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptError
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.host.ScriptLoadResult
import com.pafoid.skate.engine.scripting.host.ScriptSource
import com.pafoid.skate.engine.scripting.runtime.ScriptRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KotlinScriptHostTest {

    private val host = KotlinScriptHost()

    @Test
    fun KotlinScriptHost_RunsScriptAgainstEntityTransform() {
        val go = GameObject("mover")
        val transform = Transform()
        go.addComponent(transform)
        val component = ScriptComponent("mover.kts", mutableMapOf("speed" to "2"))
        go.addComponent(component)
        val logs = mutableListOf<String>()
        val source = """
            val speed = 0f
            onCreate { ctx -> ctx.log.info("created " + ctx.entity.name) }
            onUpdate { ctx, dt -> ctx.entity.transform.position.x += ctx.properties["speed"]!!.toFloat() * dt }
        """.trimIndent()
        val registry = ScriptHostRegistry().apply { register(host) }
        val rt = ScriptRuntime(
            registry, sceneRoot = { Scene() },
            logSink = { _, m -> logs += m }, readSource = { source },
        )

        rt.update(listOf(component), 0.5f)

        assertEquals(1f, transform.translation.x, 1e-4f)
        assertTrue(logs.any { it.contains("created mover") })
    }

    @Test
    fun KotlinScriptHost_CompileErrorHasFileAndLine() {
        val result = host.load(ScriptSource("broken.kts", "val a = 1\nval b: Int = \"x\""))

        val failure = assertIs<ScriptLoadResult.Failure>(result)
        val error: ScriptError = failure.errors.first()
        assertEquals("broken.kts", error.path)
        assertEquals(2, error.line)
    }

    @Test
    fun KotlinScriptHost_CachesCompiledScriptsByContent() {
        val src = ScriptSource("same.kts", "onCreate { }")
        assertIs<ScriptLoadResult.Success>(host.load(src))
        val second = assertIs<ScriptLoadResult.Success>(host.load(src))

        assertEquals(1, host.compilations)
        assertNotNull(second.factory.instantiate())

        host.reload(src)
        assertEquals(2, host.compilations)
    }

    @Test
    fun KotlinScriptHost_ScriptBodyExceptionSurfacesAsInstantiationFailure() {
        val errors = mutableListOf<ScriptError>()
        val go = GameObject("e").also { it.addComponent(Transform()) }
        val component = ScriptComponent("throws.kts")
        go.addComponent(component)
        val rt = ScriptRuntime(
            ScriptHostRegistry().apply { register(host) }, sceneRoot = { Scene() },
            onError = { errors += it }, readSource = { "error(\"nope\")" },
        )

        rt.update(listOf(component), 0.01f)

        assertEquals(1, errors.size)
        assertTrue(errors.single().message.contains("nope"))
    }
}
