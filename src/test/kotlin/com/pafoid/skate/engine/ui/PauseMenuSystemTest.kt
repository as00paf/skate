package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GUI-09: the shared pause menu, run as Engine wires it (cancel fallback, freeze through SystemManager.paused). */
class PauseMenuSystemTest {

    private class Simulation : System() {
        var time = 0f
        override fun update(dt: Float) { time += dt }
    }

    private var frame = UiInputFrame()
    private val deferred = ArrayDeque<() -> Unit>()
    private var restarts = 0
    private var quits = 0
    private var volume = 0.4f
    private var language = "en"

    private val manager = SystemManager()
    private val sim = Simulation()
    private val pause = PauseMenuSystem(
        setFrozen = { manager.paused = it },
        actions = PauseMenuActions(restart = { restarts++ }, quit = { quits++ }),
        toggleLanguage = { language = if (language == "fr") "en" else "fr" },
        volume = { volume },
        setVolume = { volume = it },
        defer = { deferred.addLast(it) },
    )
    private val input = UiInputSystem { frame }.also { it.onUnhandledCancel = pause::open }
    private val scene = Scene("Level")

    init {
        listOf(sim, pause, UiLayoutSystem(screen = { UiScreenSize(1920, 1080) }), input).forEach { manager.addSystem(it) }
        manager.start()
        manager.loadScene(scene)
    }

    /** One Engine frame: systems, then the work deferred to the end of the frame. */
    private fun step(f: UiInputFrame = UiInputFrame()) {
        frame = f
        manager.update(1f / 60f)
        while (deferred.isNotEmpty()) deferred.removeFirst().invoke()
    }

    private fun esc() = step(UiInputFrame(cancel = true))

    private fun click(name: String) {
        val go = PauseMenu.find(pause.menu, name)!!
        val r = go.getComponent<UIElementComponent>()!!.rect
        val s = pause.menu.getComponent<UICanvasComponent>()!!.scale
        val x = (r.x + r.w / 2f) * s
        val y = (r.y + r.h / 2f) * s
        step(UiInputFrame(x, y))
        step(UiInputFrame(x, y, pointerDown = true, pointerPressed = true))
        step(UiInputFrame(x, y, pointerReleased = true))
    }

    private fun play() {
        scene.isRunning = true
        step()
    }

    @Test
    fun Esc_TogglesThePauseMenu_AndFreezesTheSimulation() {
        play()
        assertSame(scene, pause.menu.parent, "menu joins the playing scene")
        esc()
        assertTrue(pause.isOpen)
        assertTrue(manager.paused)
        val frozen = sim.time
        repeat(30) { step() }
        assertEquals(frozen, sim.time, "simulation advanced while paused")

        esc()
        assertFalse(pause.isOpen)
        assertFalse(manager.paused)
        step()
        assertTrue(sim.time > frozen)
    }

    @Test
    fun Esc_WhileEditing_DoesNothing() {
        step()
        esc()
        assertFalse(pause.isOpen)
        assertFalse(manager.paused)
        assertNull(pause.menu.parent, "the menu is never part of the edited scene (or its pre-play snapshot)")
    }

    @Test
    fun GameCanvasWithOwnCancel_ClosesFirst() {
        var dialogClosed = 0
        scene.addChild(GameObject("Dialog").apply {
            addComponent(UICanvasComponent().also { it.onCancel = { dialogClosed++ } })
        })
        play()
        esc()
        assertEquals(1, dialogClosed)
        assertFalse(pause.isOpen)
    }

    @Test
    fun Resume_Restart_Quit_Language_Volume() {
        play()
        esc()
        assertEquals(0.4f, PauseMenu.find(pause.menu, PauseMenu.VOLUME)!!.getComponent<UISliderComponent>()!!.value)

        click(PauseMenu.LANGUAGE)
        assertEquals("fr", language)
        assertTrue(pause.isOpen)

        click(PauseMenu.RESUME)
        assertFalse(pause.isOpen)
        assertFalse(manager.paused)

        esc()
        click(PauseMenu.RESTART)
        assertEquals(1, restarts)
        assertFalse(pause.isOpen, "Restart resumes play")
        assertFalse(manager.paused)

        esc()
        click(PauseMenu.QUIT)
        assertEquals(1, quits)
    }

    @Test
    fun StopWhilePaused_ClosesAndRemovesTheMenu() {
        play()
        esc()
        scene.isRunning = false
        step()
        assertFalse(pause.isOpen)
        assertFalse(manager.paused)
        assertNull(pause.menu.parent)
    }

    @Test
    fun SceneReload_MovesTheMenuToTheNewScene() {
        play()
        val reloaded = Scene("Level").also { it.isRunning = true }
        manager.loadScene(reloaded)
        step()
        assertSame(reloaded, pause.menu.parent)
        assertFalse(scene.children.contains(pause.menu))
    }
}
