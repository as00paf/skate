package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import com.pafoid.skate.engine.ecs.config.ExecutionPriority
import com.pafoid.skate.engine.getComponent

/**
 * System responsible for managing environment settings via components.
 *
 * This system runs at [com.pafoid.skate.engine.ecs.config.ExecutionPriority.EARLY] to ensure environment state
 * is ready before rendering systems read from EnvironmentComponent.
 *
 * ## Responsibilities
 *
 * - Ensures Scene has EnvironmentComponent
 * - Provides ImGui interface for real-time environment editing
 * - Supports environment presets for quick configuration
 * - Integrates with DayNightCycleSystem for coordinated lighting
 *
 * ## Usage
 *
 * ```kotlin
 * val environmentSystem = EnvironmentSystem(stringManager)
 * scene.addSystem(environmentSystem)
 *
 * // EnvironmentComponent is automatically added to Scene
 * // Other systems read from Scene's EnvironmentComponent
 * val envComponent = scene.getComponent<EnvironmentComponent>()
 * envComponent?.fogDensity = 0.01f
 * ```
 *
 */
class EnvironmentSystem : System(priority = ExecutionPriority.EARLY) {

    // Reference to Scene's EnvironmentComponent (updated each frame)
    private var environmentComponent: EnvironmentComponent? = null

    fun getEnvironmentComponent(): EnvironmentComponent? {
        return scene.getComponent<EnvironmentComponent>()
    }

    private fun getOrCreateEnvironmentComponent(): EnvironmentComponent {
        val existingComponent = getEnvironmentComponent()
        if (existingComponent != null) {
            environmentComponent = existingComponent
            return existingComponent
        }

        val createdComponent = EnvironmentComponent()
        scene.addComponent(createdComponent)
        environmentComponent = createdComponent
        return createdComponent
    }

    fun applyPreset(preset: EnvironmentPreset) {
        getOrCreateEnvironmentComponent().applyPreset(preset)
    }

    fun reset() {
        getEnvironmentComponent()?.reset()
    }

    override fun update(dt: Float) {
        getOrCreateEnvironmentComponent().update(dt)
    }

    override fun invalidateCache() {
        environmentComponent = null
    }
}
