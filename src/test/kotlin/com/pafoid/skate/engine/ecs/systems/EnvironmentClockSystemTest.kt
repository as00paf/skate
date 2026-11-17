package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-29 §5.3: the clock steps with fixed updates, before physics, and freezes while paused. */
class EnvironmentClockSystemTest {
    @Test
    fun ClockSystem_RunsInFixedUpdateBeforePhysics() {
        val system = EnvironmentClockSystem()
        assertEquals(SystemPhase.FIXED_UPDATE, system.phase)
        assertTrue(PhysicsSystem::class in system.runsBefore)
    }

    @Test
    fun ClockSystem_StepsTheSceneEnvironmentClock() {
        val env = EnvironmentComponent()
        val scene = Scene("S").addComponent(env) as Scene
        val system = EnvironmentClockSystem().apply { init(scene) }

        system.fixedUpdate(0.5f)
        system.fixedUpdate(0.5f)

        assertEquals(1f, env.runtime.animationTime, 1e-6f)
    }

    @Test
    fun ClockSystem_PausedManager_DoesNotStepTheClock() {
        val env = EnvironmentComponent()
        val scene = Scene("S").addComponent(env) as Scene
        val manager = SystemManager()
        manager.addSystem(EnvironmentClockSystem())
        manager.loadScene(scene)
        manager.update(0.1f)
        val before = env.runtime.animationTime
        manager.paused = true
        repeat(10) { manager.update(0.1f) }
        assertEquals(before, env.runtime.animationTime)
    }
}
