package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GUI-09: the runtime pause freezes simulation systems and keeps input/UI/render ones running. */
class SystemManagerPauseTest {

    private open class Counting(override val phase: SystemPhase, private val aware: Boolean = false) : System() {
        override val runsWhilePaused get() = aware
        var updates = 0
        var fixed = 0
        var renders = 0
        override fun update(dt: Float) { updates++ }
        override fun fixedUpdate(fixedDt: Float) { fixed++ }
        override fun render(alpha: Float) { renders++ }
    }

    private class Sim : Counting(SystemPhase.UPDATE)
    private class Physics : Counting(SystemPhase.FIXED_UPDATE)
    private class Ui : Counting(SystemPhase.LATE_UPDATE, aware = true)
    private class Draw : Counting(SystemPhase.RENDER)

    private val sim = Sim()
    private val physics = Physics()
    private val ui = Ui()
    private val draw = Draw()
    private val scene = Scene("S")
    private val manager = SystemManager().apply {
        listOf(sim, physics, ui, draw).forEach { addSystem(it) }
        start()
        loadScene(scene)
    }

    private fun frames(n: Int) = repeat(n) { manager.update(1f / 60f); manager.render() }

    @Test
    fun Paused_OnlyPauseAwareAndRenderSystemsRun_NoFixedSteps() {
        frames(3)
        assertEquals(3, sim.updates)
        assertTrue(physics.fixed >= 2)
        val fixedBefore = physics.fixed

        manager.paused = true
        frames(10)
        assertEquals(3, sim.updates, "simulation must freeze")
        assertEquals(fixedBefore, physics.fixed, "no fixed steps while paused")
        assertEquals(13, ui.updates, "UI keeps running to drive the pause menu")
        assertEquals(13, draw.renders)
        assertTrue(scene.isPaused)
    }

    @Test
    fun Resume_ContinuesWithoutCatchUpSteps() {
        frames(2)
        manager.paused = true
        frames(120) // 2 s paused must not turn into a burst of fixed steps on resume
        val fixedBefore = physics.fixed
        manager.paused = false
        frames(1)
        assertFalse(scene.isPaused)
        assertEquals(3, sim.updates)
        assertTrue(physics.fixed - fixedBefore <= 1, "resume ran ${physics.fixed - fixedBefore} fixed steps")
    }
}
