package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.events.ViewportAction.SetSimulationTimeScale
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.getComponent
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** REN-23: Play/Stop and the toolbar's simulation speed never touch the scene's saved day/night timeScale. */
class DayNightTimeScaleTest {
    private val serializer = Serializer()
    private val events = EventSystem()

    private fun handlerFor(scene: Scene) {
        val sceneManager = mockk<SceneManager>(relaxed = true) { every { currentScene } returns scene; every { openScenes } returns mutableListOf(scene) }
        val engine = mockk<Engine>(relaxed = true) {
            every { eventSystem } returns events
            every { this@mockk.sceneManager } returns sceneManager
            every { this@mockk.serializer } returns this@DayNightTimeScaleTest.serializer
        }
        val undo = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))
        ViewportActionHandler(engine, undo, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)).init()
    }

    private fun sceneWithTimeScale(scale: Float) = Scene("Main").also { it.addComponent(DayNightCycleComponent(timeScale = scale)) }

    @Test
    fun SavedTimeScale_SurvivesPlayStop() {
        val scene = sceneWithTimeScale(2.5f)
        handlerFor(scene)
        val cycle = scene.getComponent<DayNightCycleComponent>()!!

        events.publish(EngineAction.SetRuntimePlaying(true))
        events.publish(SetSimulationTimeScale(0f))
        assertEquals(0f, cycle.playTimeScale)
        assertEquals(2.5f, cycle.timeScale)

        events.publish(EngineAction.SetRuntimePlaying(false))
        assertEquals(2.5f, cycle.timeScale)
        assertEquals(1f, cycle.playTimeScale)
    }

    @Test
    fun SavedTimeScale_SurvivesSaveAndReload_PlayScaleIsNotSaved() {
        val scene = sceneWithTimeScale(3f)
        scene.getComponent<DayNightCycleComponent>()!!.playTimeScale = 0f

        val loaded = serializer.decode<Scene>(serializer.encode(scene))
        val cycle = loaded.getComponent<DayNightCycleComponent>()!!
        assertEquals(3f, cycle.timeScale)
        assertEquals(1f, cycle.playTimeScale)

        handlerFor(loaded)
        events.publish(EngineAction.SetRuntimePlaying(true))
        events.publish(EngineAction.SetRuntimePlaying(false))
        assertEquals(3f, serializer.decode<Scene>(serializer.encode(loaded)).getComponent<DayNightCycleComponent>()!!.timeScale)
    }
}
