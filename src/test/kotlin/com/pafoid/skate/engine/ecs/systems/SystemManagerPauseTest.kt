package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.ui.PauseMenuSystem
import com.pafoid.skate.engine.ui.UiInputSystem
import io.mockk.mockk
import io.mockk.verify
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

    @Test
    fun Paused_MirrorsToSceneImmediately() {
        manager.paused = true
        assertTrue(scene.isPaused, "systems later in the same frame must see the pause")
        manager.paused = false
        assertFalse(scene.isPaused)
    }

    @Test
    fun SceneSwitch_ClearsPause() {
        manager.paused = true
        val other = Scene("Other")
        manager.loadScene(other)
        assertFalse(manager.paused)
        assertFalse(scene.isPaused, "the previous scene must not stay paused")
        assertFalse(other.isPaused)
        frames(1)
        assertEquals(1, sim.updates)
    }

    @Test
    fun Paused_PhysicsDoesNotStep() {
        val physics3d = mockk<IPhysics3D>(relaxed = true)
        val running = Scene("Run").apply { isRunning = true }
        val m = SystemManager().apply {
            addSystem(PhysicsSystem(mockk(relaxed = true), null, physics3d))
            start()
            loadScene(running)
        }
        m.paused = true
        repeat(30) { m.update(1f / 60f) }
        verify(exactly = 0) { physics3d.step(any()) }
        m.paused = false
        m.update(1f / 60f)
        verify(atMost = 1) { physics3d.step(any()) }
    }

    /** Which engine systems freeze: simulation and gameplay cameras freeze; input, UI, audio and derived state run. */
    @Test
    fun EngineSystems_PauseClassification() {
        val events = EventSystem()
        val frozen = listOf(
            PhysicsSystem(mockk(relaxed = true), null, mockk(relaxed = true)),
            AnimationSystem(),
            ParticleSystem(),
            ScriptingSystem(),
            CameraControlSystem { mockk(relaxed = true) },
            FollowCameraSystem(),
            CameraCycleSystem(null, { null }) { _, _ -> },
            DayNightCycleSystem(),
            RagdollSystem(),
            MountFollowSystem(),
            GameObjectManager(),
        )
        val running = listOf(
            InputSystem(mockk(relaxed = true), events),
            UiInputSystem { mockk(relaxed = true) },
            PauseMenuSystem({}),
            AudioSystem(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)),
            CameraManager(events),
            TransformSystem(),
        )
        frozen.forEach { assertFalse(it.runsWhilePaused, "${it.displayName} must freeze while paused") }
        running.forEach { assertTrue(it.runsWhilePaused, "${it.displayName} must run while paused") }
    }
}
