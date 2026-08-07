package com.pafoid.skate.engine.ecs.systems

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

    fun applyPreset(preset: EnvironmentPreset) {
        environmentComponent?.applyPreset(preset)
    }

    fun reset() {
        getEnvironmentComponent()?.reset()
    }

    override fun update(dt: Float) {
        environmentComponent?.update(dt)
    }

    override fun invalidateCache() {
        environmentComponent = null
    }
}
