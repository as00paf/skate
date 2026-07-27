package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent

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
