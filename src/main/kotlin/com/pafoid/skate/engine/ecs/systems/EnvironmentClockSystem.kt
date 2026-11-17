package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import kotlin.reflect.KClass

/**
 * Steps the wind and water animation clock ([EnvironmentComponent.runtime], REN-29 §5.3) once per fixed step, before
 * physics, so buoyancy and rendering read the same time. Ticks in Play, previews in edit mode while
 * [EnvironmentComponent.animateInEditor] is on, and freezes while paused (fixed steps do not run).
 */
class EnvironmentClockSystem : System(priority = ExecutionPriority.DEFAULT) {
    override val phase: SystemPhase get() = SystemPhase.FIXED_UPDATE
    override val runsBefore: List<KClass<out System>> get() = listOf(PhysicsSystem::class)

    override fun fixedUpdate(fixedDt: Float) {
        val env = scene.getComponent<EnvironmentComponent>() ?: return
        // Rendering interpolates between fixed steps with this system's alpha, like PhysicsSystem
        env.runtime.stepAlpha = alphaSource
        env.runtime.step(fixedDt, running = scene.isRunning, animateInEditor = env.animateInEditor)
    }

    private val alphaSource: () -> Float = { fixedStepAlpha }
}
