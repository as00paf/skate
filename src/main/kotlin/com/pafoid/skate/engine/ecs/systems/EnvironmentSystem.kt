package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import com.pafoid.skate.engine.ecs.config.FogCycle
import com.pafoid.skate.engine.environment.WindCycle
import com.pafoid.skate.engine.ui.UIButtonComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent

class EnvironmentSystem : System(priority = ExecutionPriority.EARLY) {

    fun getEnvironmentComponent(): EnvironmentComponent? {
        return scene.getComponent<EnvironmentComponent>()
    }

    fun applyPreset(preset: EnvironmentPreset) {
        getEnvironmentComponent()?.applyPreset(preset)
    }

    fun reset() {
        getEnvironmentComponent()?.reset()
    }

    private val buttons = query(UIButtonComponent::class)

    override fun update(dt: Float) {
        getEnvironmentComponent()?.update(dt)
        bindFogCycleButton()
        bindWindCycleButton()
    }

    /** Binds the button named by [EnvironmentComponent.windCycleButton] (if unbound) to [WindCycle.next] (REN-29 §11). */
    private fun bindWindCycleButton() {
        val env = getEnvironmentComponent() ?: return
        val name = env.windCycleButton.takeIf { it.isNotEmpty() } ?: return
        buttons.forEach { go ->
            if (go.name != name) return@forEach
            val button = go.getComponent<UIButtonComponent>() ?: return@forEach
            if (button.onClick == null) button.onClick = { getEnvironmentComponent()?.let(WindCycle::next) }
        }
    }

    /** Binds the button named by [EnvironmentComponent.fogCycleButton] (if unbound) to [FogCycle.next] (REN-09). */
    private fun bindFogCycleButton() {
        val env = getEnvironmentComponent() ?: return
        val name = env.fogCycleButton.takeIf { it.isNotEmpty() } ?: return
        buttons.forEach { go ->
            if (go.name != name) return@forEach
            val button = go.getComponent<UIButtonComponent>() ?: return@forEach
            // Resolve the component on click: it may be replaced after binding (e.g. re-added in the inspector).
            if (button.onClick == null) button.onClick = { getEnvironmentComponent()?.let(FogCycle::next) }
        }
    }
}
