package com.pafoid.skate.cli

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.scripting.api.ScriptInput
import com.pafoid.skate.engine.scripting.host.KotlinScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptError
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.runtime.ScriptRuntime
import com.pafoid.skate.engine.scripting.runtime.ScriptServices
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TetrisProjectBuilderTest {

    private class FakeInput : ScriptInput {
        val pressed = mutableSetOf<Int>()
        val down = mutableSetOf<Int>()
        override fun isKeyDown(key: Int) = key in down
        override fun isKeyPressed(key: Int) = key in pressed
        override fun isKeyReleased(key: Int) = false
        override fun isMouseButtonDown(button: Int) = false
        override fun action(name: String) = 0f
    }

    private val input = FakeInput()
    private val logs = mutableListOf<String>()
    private val errors = mutableListOf<ScriptError>()
    private lateinit var dir: java.io.File
    private lateinit var scene: Scene
    private lateinit var runtime: ScriptRuntime
    private lateinit var component: ScriptComponent

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("skate-tetris").toFile()
        val project = TetrisProjectBuilder().build(dir)
        val service = SkateCliService(out = {})
        scene = service.loadScenes(service.loadProject(project.path)).single()
        // Mirrors SceneManager.resolveSceneReferences, which binds components after a scene is loaded
        scene.children.forEach { go -> go.components.forEach { it.init(go) } }
        component = scene.children.first { it.name == "TetrisController" }.getComponent<ScriptComponent>()!!
        component.properties["seed"] = "7"
        val registry = ScriptHostRegistry().apply { register(KotlinScriptHost()) }
        runtime = ScriptRuntime(
            registry, ScriptServices(input = input), sceneRoot = { scene },
            onError = { errors += it }, logSink = { _, m -> logs += m },
        )
    }

    @AfterTest
    fun tearDown() {
        runtime.stop()
        dir.deleteRecursively()
    }

    private fun frame(vararg keys: Int) {
        input.pressed.clear()
        input.pressed.addAll(keys.toList())
        runtime.update(listOf(component), 1f / 60f)
    }

    private fun visibleCells() = scene.children.filter { it.name.startsWith("Cell_") }
        .count { it.getComponent<Transform>()!!.translation.y > -500f }

    @Test
    fun Tetris_PoolAndScriptAreGenerated() {
        assertEquals(TetrisProjectBuilder.POOL_SIZE, scene.children.count { it.name.startsWith("Cell_") })
        assertTrue(java.io.File(component.path).isFile)
    }

    @Test
    fun Tetris_SpawnsPieceAndFallsWithoutErrors() {
        frame()
        assertEquals(4, visibleCells())
        repeat(300) { frame() }
        assertTrue(errors.isEmpty(), errors.toString())
        assertTrue(visibleCells() >= 4)
    }

    @Test
    fun Tetris_HardDropLocksPieceAndSpawnsNext() {
        frame()
        frame(32)
        assertTrue(errors.isEmpty(), errors.toString())
        assertEquals(8, visibleCells())
    }

    @Test
    fun Tetris_StackingToTheTopEndsGameAndRestartRecovers() {
        frame()
        repeat(30) { frame(32) }
        assertTrue(logs.any { it.contains("gameover") }, logs.toString())
        frame(82)
        assertTrue(logs.last().contains("start"), logs.toString())
        assertEquals(4, visibleCells())
        assertTrue(errors.isEmpty(), errors.toString())
    }
}
