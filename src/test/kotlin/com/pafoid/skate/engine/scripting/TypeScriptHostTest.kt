package com.pafoid.skate.engine.scripting

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.scripting.host.ScriptError
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.host.ScriptLoadResult
import com.pafoid.skate.engine.scripting.host.ScriptSource
import com.pafoid.skate.engine.scripting.host.TypeScriptHost
import com.pafoid.skate.engine.scripting.runtime.ScriptRuntime
import org.junit.jupiter.api.AfterAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TypeScriptHostTest {

    private class Fixture(path: String, var text: String, props: Map<String, String> = emptyMap()) {
        val transform = Transform()
        val logs = mutableListOf<String>()
        val errors = mutableListOf<ScriptError>()
        val component = ScriptComponent(path, props.toMutableMap()).also {
            GameObject("hero").apply { addComponent(transform); addComponent(it) }
        }
        val runtime = ScriptRuntime(
            ScriptHostRegistry().apply { register(host) },
            sceneRoot = { Scene() },
            onError = { errors += it },
            logSink = { _, m -> logs += m },
            readSource = { text },
            reloadCheckInterval = 0f,
        )

        fun tick(dt: Float = 0.5f) = runtime.update(listOf(component), dt)
    }

    @Test
    fun TypeScriptHost_RunsTypedScriptAgainstContext() {
        val f = Fixture(
            "mover.ts",
            """
            interface Ctx { entity: any; properties: any; log: any; state: any }
            let total: number = 0;
            export function onCreate(ctx: Ctx): void {
                ctx.log.info("hello " + ctx.entity.name);
                ctx.state.set("seen", 1);
            }
            export function onUpdate(ctx: Ctx, dt: number): void {
                total += dt;
                ctx.entity.transform.position.x = total * Number(ctx.properties.speed);
                ctx.entity.transform.position.y = ctx.state.get("seen");
            }
            """.trimIndent(),
            mapOf("speed" to "4"),
        )

        f.tick()

        assertEquals(emptyList(), f.errors)
        assertEquals(2f, f.transform.translation.x, 1e-4f)
        assertEquals(1f, f.transform.translation.y, 1e-4f)
        assertTrue(f.logs.any { it.contains("hello hero") })
    }

    @Test
    fun TypeScriptHost_GlobalFunctionsAndPlainJavaScriptWork() {
        val f = Fixture("plain.js", "function onUpdate(ctx, dt) { ctx.entity.transform.position.z = 9; }")

        f.tick()

        assertEquals(9f, f.transform.translation.z, 1e-4f)
    }

    @Test
    fun TypeScriptHost_SyntaxErrorHasFileAndLine() {
        val result = host.load(ScriptSource("bad.ts", "let a = 1;\nlet = = 2;"))

        val error = assertIs<ScriptLoadResult.Failure>(result).errors.first()
        assertEquals("bad.ts", error.path)
        assertEquals(2, error.line)
    }

    @Test
    fun TypeScriptHost_RuntimeErrorReportedWithLine() {
        val f = Fixture("boom.ts", "export function onUpdate(ctx: any, dt: number) {\n  throw new Error('kaboom');\n}")

        f.tick()

        val error = f.errors.single()
        assertEquals("boom.ts", error.path)
        assertEquals(2, error.line)
        assertTrue(error.message.contains("kaboom"))
    }

    @Test
    fun TypeScriptHost_ScriptsCannotReachHostInternals() {
        val f = Fixture("evil.ts", "export function onCreate(ctx: any) { (globalThis as any).Java.type('java.lang.System'); }")

        f.tick()

        assertTrue(f.errors.single().message.contains("not allowed"), f.errors.joinToString())
    }

    @Test
    fun TypeScriptHost_HotReloadSwapsScript() {
        val f = Fixture("r.ts", "export function onUpdate(ctx: any, dt: number) { ctx.entity.transform.position.x = 1; }")
        f.tick()
        f.text = "export function onUpdate(ctx: any, dt: number) { ctx.entity.transform.position.x = 2; }"
        f.tick()

        assertEquals(2f, f.transform.translation.x, 1e-4f)
        assertEquals(emptyList(), f.errors)
    }

    companion object {
        val host = TypeScriptHost()

        @JvmStatic
        @AfterAll
        fun close() = host.close()
    }
}
