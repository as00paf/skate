package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.events.PlayModeAction
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.systems.PlayMode
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.getComponent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

/** EDT-45: Play/Stop/Reset against a real SceneManager with several open scenes (review B1, N5). */
class PlayModeHandlerTest {
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
    }
    private val undo = mockk<UndoRedoManager>(relaxed = true)
    private val handler = PlayModeHandler(engine, undo).also { it.init() }

    init {
        // What Engine does with the event.
        events.subscribe<EngineAction.SetRuntimePlaying> { playing = it.playing }
    }

    private fun scene(name: String, y: Float) = Scene(name).apply {
        addChild(GameObject("Box").addComponent(Transform(Vector3f(0f, y, 0f))))
    }

    private fun Scene.boxY() = children.single { it.name == "Box" }.getComponent<Transform>()!!.translation.y

    private fun open(sceneManager: SceneManager): Pair<Scene, Scene> {
        val main = scene("Main", 1f)
        val showcase = scene("Showcase", 2f)
        sceneManager.openScene(main)
        sceneManager.openScene(showcase)
        sceneManager.switchScene(main)
        return main to showcase
    }

    /** Play, switch to the second tab and let both scenes change. */
    private fun playAndChangeBoth(main: Scene, showcase: Scene) {
        events.publish(PlayModeAction.Play)
        handler.update()
        main.children[0].getComponent<Transform>()!!.translation.y = -10f
        sceneManager.switchScene(showcase)
        showcase.isRunning = true
        showcase.children[0].getComponent<Transform>()!!.translation.y = -20f
        showcase.addChild(GameObject("Spawned"))
    }

    @Test
    fun PlayModeHandler_ActionsWaitForTheNextFrame() {
        open(sceneManager)
        events.publish(PlayModeAction.Play)
        assertEquals(PlayMode.EDIT, handler.controller.mode)
        handler.update()
        assertEquals(PlayMode.PLAYING, handler.controller.mode)
    }

    @Test
    fun PlayModeHandler_StopRestoresEveryOpenSceneAndTheActiveOne() {
        val (main, showcase) = open(sceneManager)
        playAndChangeBoth(main, showcase)

        events.publish(PlayModeAction.Stop)
        handler.update()

        assertEquals(PlayMode.EDIT, handler.controller.mode)
        val (newMain, newShowcase) = sceneManager.openScenes
        assertNotSame(main, newMain)
        assertNotSame(showcase, newShowcase)
        assertEquals(listOf("Main", "Showcase"), sceneManager.openScenes.map { it.name })
        assertEquals(1f, newMain.boxY())
        assertEquals(2f, newShowcase.boxY())
        assertEquals(listOf("Box"), newShowcase.children.map { it.name })
        assertEquals("Main", sceneManager.currentScene?.name)
        sceneManager.openScenes.forEach { assertFalse(it.isRunning, it.name) }
        verify { undo.clear() }
    }

    @Test
    fun PlayModeHandler_ResetRestoresEverySceneAndKeepsPlaying() {
        val (main, showcase) = open(sceneManager)
        playAndChangeBoth(main, showcase)

        events.publish(PlayModeAction.Reset)
        handler.update()

        assertEquals(PlayMode.PLAYING, handler.controller.mode)
        assertEquals(1f, sceneManager.openScenes[0].boxY())
        assertEquals(2f, sceneManager.openScenes[1].boxY())
        assertEquals("Main", sceneManager.currentScene?.name)
    }

    @Test
    fun PlayModeHandler_SceneClosedDuringPlayIsSkipped() {
        val (main, showcase) = open(sceneManager)
        playAndChangeBoth(main, showcase)
        sceneManager.closeScene(main)

        events.publish(PlayModeAction.Stop)
        handler.update()

        assertEquals(listOf("Showcase"), sceneManager.openScenes.map { it.name })
        assertEquals(2f, sceneManager.openScenes[0].boxY())
    }

    @Test
    fun PlayModeHandler_ProjectClosingStopsAtOnceAndDropsQueuedActions() {
        val (main, showcase) = open(sceneManager)
        playAndChangeBoth(main, showcase)
        events.publish(PlayModeAction.Reset)

        events.publish(ProjectEvent.Closing(mockk(relaxed = true)))

        assertEquals(PlayMode.EDIT, handler.controller.mode)
        assertEquals(1f, sceneManager.openScenes[0].boxY())
        handler.update()
        assertEquals(PlayMode.EDIT, handler.controller.mode)
    }
}
