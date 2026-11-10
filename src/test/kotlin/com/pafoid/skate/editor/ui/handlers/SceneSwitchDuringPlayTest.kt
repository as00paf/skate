package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.events.PlayModeAction
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.loading.LoadingRunner
import com.pafoid.skate.editor.loading.StepExecutor
import com.pafoid.skate.editor.systems.PlayMode
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.getComponent
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-63: a scene change the user makes during Play stops Play (restoring every scene) first; script switches keep playing. */
class SceneSwitchDuringPlayTest {
    private val events = EventSystem()
    private val serializer = Serializer()
    private val sceneManager = SceneManager(mockk(relaxed = true), events, serializer, mockk(relaxed = true), mockk(relaxed = true))
    private var playing = false
    private val engine = mockk<Engine>(relaxed = true).also {
        every { it.eventSystem } returns events
        every { it.sceneManager } returns sceneManager
        every { it.serializer } returns serializer
        every { it.runtimePlaying } answers { playing }
        every { it.paused } returns false
        every { it.logger } returns LoggerService()
    }
    private val undo = UndoRedoManager(events, LoggerService())
    private val projectManager = mockk<ProjectManager>(relaxed = true).also {
        every { it.getProjectDirectory() } returns null
    }
    private val playMode = PlayModeHandler(engine, undo).also { it.init() }

    init {
        // What Engine does with the event.
        events.subscribe<EngineAction.SetRuntimePlaying> { playing = it.playing }
        // Real camera objects: the handler remembers the editor camera per scene (EDT-58)
        val editorCamera = mockk<EditorCamera>(relaxed = true).also {
            every { it.camera } returns CameraComponent()
            every { it.transform } returns Transform()
        }
        // Loading steps run inline (EDT-56)
        val inline = object : StepExecutor {
            override fun background(block: () -> Unit) = block()
            override fun main(block: () -> Unit) = block()
        }
        SceneActionHandler(engine, projectManager, undo, editorCamera, LoadingRunner(events, inline))
    }

    private fun scene(name: String, y: Float) = Scene(name).apply {
        addChild(GameObject("Box").addComponent(Transform(Vector3f(0f, y, 0f))))
    }

    private fun Scene.boxY() = children.single { it.name == "Box" }.getComponent<Transform>()!!.translation.y

    /** Opens Main and Showcase, plays on Main and moves Main's box. */
    private fun playOnMain(): Pair<Scene, Scene> {
        val main = scene("Main", 1f)
        val showcase = scene("Showcase", 2f)
        sceneManager.openScene(main)
        sceneManager.openScene(showcase)
        sceneManager.switchScene(main)
        events.publish(PlayModeAction.Play)
        playMode.update()
        main.children[0].getComponent<Transform>()!!.translation.y = -10f
        return main to showcase
    }

    @Test
    fun SceneSwitch_InEdit_SwitchesRightAway() {
        val main = scene("Main", 1f)
        val showcase = scene("Showcase", 2f)
        sceneManager.openScene(main)
        sceneManager.openScene(showcase)
        sceneManager.switchScene(main)

        events.publish(ViewportAction.TabSelected(showcase))

        assertEquals("Showcase", sceneManager.currentScene?.name)
        assertEquals(PlayMode.EDIT, playMode.controller.mode)
    }

    @Test
    fun SceneSwitch_TabDuringPlay_StopsRestoresThenSwitches() {
        val (_, showcase) = playOnMain()

        events.publish(ViewportAction.TabSelected(showcase))
        // Nothing happens while ImGui is drawing: the stop and the switch run at the start of the next frame
        assertEquals(PlayMode.PLAYING, playMode.controller.mode)
        assertEquals("Main", sceneManager.currentScene?.name)

        playMode.update()

        assertEquals(PlayMode.EDIT, playMode.controller.mode)
        assertEquals("Showcase", sceneManager.currentScene?.name)
        assertEquals(1f, sceneManager.openScenes.single { it.name == "Main" }.boxY())
        sceneManager.openScenes.forEach { assertFalse(it.isRunning, it.name) }
    }

    @Test
    fun SceneSwitch_TabWhilePaused_AlsoStops() {
        val (_, showcase) = playOnMain()
        every { engine.paused } returns true

        events.publish(ViewportAction.TabSelected(showcase))
        playMode.update()

        assertEquals(PlayMode.EDIT, playMode.controller.mode)
        assertEquals("Showcase", sceneManager.currentScene?.name)
    }

    @Test
    fun SceneSwitch_OpenSceneFileDuringPlay_StopsBeforeOpening() {
        playOnMain()
        val modeWhenOpening = mutableListOf<PlayMode>()
        events.subscribe<SceneAction.OpenFailed> { modeWhenOpening += playMode.controller.mode }

        events.publish(SceneAction.OpenSceneFile(File("missing-${System.nanoTime()}.scene")))
        assertEquals(emptyList<PlayMode>(), modeWhenOpening)
        playMode.update()

        assertEquals(listOf(PlayMode.EDIT), modeWhenOpening)
        assertEquals(1f, sceneManager.openScenes.single { it.name == "Main" }.boxY())
    }

    @Test
    fun SceneSwitch_ScriptSwitchDuringPlay_KeepsPlaying() {
        val (_, showcase) = playOnMain()

        // ScriptingSystem applies a script's Scene.load by switching the SceneManager directly
        sceneManager.switchScene(showcase)
        playMode.update()

        assertEquals(PlayMode.PLAYING, playMode.controller.mode)
        assertEquals("Showcase", sceneManager.currentScene?.name)
        assertEquals(-10f, sceneManager.openScenes.single { it.name == "Main" }.boxY())
    }

    @Test
    fun SceneSwitch_StopThen_RunsAfterTheRestore() {
        playOnMain()
        var boxYSeenByAction = 0f
        events.publish(PlayModeAction.StopThen { boxYSeenByAction = sceneManager.currentScene!!.boxY() })

        playMode.update()

        assertEquals(1f, boxYSeenByAction)
    }
}
