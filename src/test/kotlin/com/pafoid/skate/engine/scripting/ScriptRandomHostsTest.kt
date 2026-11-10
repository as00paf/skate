package com.pafoid.skate.engine.scripting

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.random.RandomService
import com.pafoid.skate.engine.scripting.host.KotlinScriptHost
import com.pafoid.skate.engine.scripting.host.LuaScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptError
import com.pafoid.skate.engine.scripting.host.ScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.host.TypeScriptHost
import com.pafoid.skate.engine.scripting.runtime.ScriptRuntime
import com.pafoid.skate.engine.scripting.runtime.ScriptServices
import com.pafoid.skate.engine.ui.UITextComponent
import com.pafoid.skate.game.samples.FeatureShowcase
import kotlin.test.Test
import kotlin.test.assertEquals

/** ECS-14: `ctx.random` behaves the same in Kotlin, Lua and TypeScript scripts. */
class ScriptRandomHostsTest {
    private val seed = 42L

    private fun run(host: ScriptHost, path: String, text: String, root: Scene = Scene()): List<String> {
        val logs = mutableListOf<String>()
        val errors = mutableListOf<ScriptError>()
        val component = ScriptComponent(path).also { GameObject("e").apply { addComponent(Transform()); addComponent(it) } }
        val runtime = ScriptRuntime(
            ScriptHostRegistry().apply { register(host) },
            services = ScriptServices(random = RandomService(seed)),
            sceneRoot = { root },
            onError = { errors += it },
            logSink = { _, m -> logs += m.substringAfter("] ") },
            readSource = { text },
        )
        runtime.update(listOf(component), 0.1f)
        assertEquals(emptyList(), errors)
        return logs
    }

    private fun expected(): String {
        val dice = RandomService(seed).stream("dice")
        return "$seed " + List(3) { dice.nextInt(1, 7) }.joinToString(" ")
    }

    @Test
    fun KotlinLuaAndTypeScript_DrawTheSameNamedStreamValues() {
        val kotlin = run(
            KotlinScriptHost(), "r.kts",
            """onCreate { ctx -> val d = ctx.random.stream("dice"); ctx.log.info("${'$'}{ctx.random.seed} " + List(3) { d.nextInt(1, 7) }.joinToString(" ")) }""",
        )
        val lua = run(
            LuaScriptHost(), "r.lua",
            """
            function onCreate(ctx)
              local d = ctx.random.stream("dice")
              ctx.log.info(string.format("%d %d %d %d", ctx.random.seed, d.nextInt(1, 7), d.nextInt(1, 7), d.nextInt(1, 7)))
            end
            """.trimIndent(),
        )
        val tsHost = TypeScriptHost()
        val ts = try {
            run(
                tsHost, "r.ts",
                """
                export function onCreate(ctx: any): void {
                    const d = ctx.random.stream("dice");
                    ctx.log.info(ctx.random.seed + " " + [d.nextInt(1, 7), d.nextInt(1, 7), d.nextInt(1, 7)].join(" "));
                }
                """.trimIndent(),
            )
        } finally {
            tsHost.close()
        }
        assertEquals(listOf(expected()), kotlin)
        assertEquals(listOf(expected()), lua)
        assertEquals(listOf(expected()), ts)
    }

    @Test
    fun ShowcaseSeedScript_FillsTheSeedLabel() {
        val text = FeatureShowcase::class.java.getResourceAsStream("/samples/showcase/${FeatureShowcase.SEED_SCRIPT}")!!
            .bufferedReader().use { it.readText() }
        val label = UITextComponent("showcase.seed", listOf("?", "?"))
        val root = Scene().apply { addChild(GameObject(FeatureShowcase.SEED_LABEL).addComponent(label)) }
        run(KotlinScriptHost(), FeatureShowcase.SEED_SCRIPT, text, root)
        val dice = RandomService(seed).stream("showcase.dice")
        assertEquals(listOf(seed.toString(), List(5) { dice.nextInt(1, 7) }.joinToString(" ")), label.args)
    }
}
