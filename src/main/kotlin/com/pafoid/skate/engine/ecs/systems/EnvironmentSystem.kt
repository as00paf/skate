package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import com.pafoid.skate.engine.ecs.config.FogCycle
import com.pafoid.skate.engine.ui.UIButtonComponent
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

    private val buttons = query(UIButtonComponent::class)

    override fun update(dt: Float) {
        environmentComponent?.update(dt)
        bindFogCycleButton()
    }

    /** Binds the button named by [EnvironmentComponent.fogCycleButton] (if unbound) to [FogCycle.next] (REN-09). */
    private fun bindFogCycleButton() {
        val env = getEnvironmentComponent() ?: return
        val name = env.fogCycleButton.takeIf { it.isNotEmpty() } ?: return
        buttons.forEach { go ->
            if (go.name != name) return@forEach
            val button = go.getComponent<UIButtonComponent>() ?: return@forEach
            if (button.onClick == null) button.onClick = { FogCycle.next(env) }
        }
    }

    override fun invalidateCache() {
        environmentComponent = null
    }
}
