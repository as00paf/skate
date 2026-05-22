package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import com.pafoid.skate.engine.ecs.config.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.type.ImBoolean

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
 * @param stringManager String manager for localized UI strings
 */
class EnvironmentSystem(
    private val stringManager: StringManager
) : System(priority = ExecutionPriority.EARLY) {

    // Reference to Scene's EnvironmentComponent (updated each frame)
    private var environmentComponent: EnvironmentComponent? = null

    private fun getEnvironmentComponent(): EnvironmentComponent? {
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

    override fun imgui() {
        val component = getEnvironmentComponent() ?: return

        ImGui.text(stringManager.getString("lbl.environment_system.header"))
        ImGui.separator()

        // Preset section
        ImGui.text(stringManager.getString("lbl.environment_system.presets"))

        if (ImGui.button(stringManager.getString("lbl.environment_system.preset_clear_day"))) {
            applyPreset(EnvironmentPreset.CLEAR_DAY)
        }
        ImGui.sameLine()

        if (ImGui.button(stringManager.getString("lbl.environment_system.preset_cloudy"))) {
            applyPreset(EnvironmentPreset.CLOUDY)
        }
        ImGui.sameLine()

        if (ImGui.button(stringManager.getString("lbl.environment_system.preset_foggy"))) {
            applyPreset(EnvironmentPreset.FOGGY)
        }
        ImGui.sameLine()

        if (ImGui.button(stringManager.getString("lbl.environment_system.preset_sunset"))) {
            applyPreset(EnvironmentPreset.SUNSET)
        }
        ImGui.sameLine()

        if (ImGui.button(stringManager.getString("lbl.environment_system.preset_no_fog"))) {
            applyPreset(EnvironmentPreset.NO_FOG)
        }

        ImGui.separator()

        // Sky configuration section
        if (ImGui.collapsingHeader(stringManager.getString("lbl.environment_system.sky_header"))) {
            // Render sky toggle
            val renderSky = ImBoolean(component.renderSky)
            if (ImGui.checkbox(stringManager.getString("lbl.environment_system.render_sky"), renderSky)) {
                component.renderSky = renderSky.get()
            }

            // Sky color
            val skyColorArr = floatArrayOf(component.skyColor.x, component.skyColor.y, component.skyColor.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment_system.sky_color"), skyColorArr)) {
                component.skyColor.set(skyColorArr[0], skyColorArr[1], skyColorArr[2])
            }

            // Sky tint
            val skyTintArr = floatArrayOf(component.skyTint.x, component.skyTint.y, component.skyTint.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment_system.sky_tint"), skyTintArr)) {
                component.skyTint.set(skyTintArr[0], skyTintArr[1], skyTintArr[2])
            }

            // Sky exposure
            val exposureArr = floatArrayOf(component.skyExposure)
            ImGui.pushItemWidth(120f)
            if (ImGui.dragFloat(
                    stringManager.getString("lbl.environment_system.sky_exposure"),
                    exposureArr,
                    0.01f,
                    0.0f,
                    10.0f,
                    "%.2f"
                )
            ) {
                component.skyExposure = exposureArr[0].coerceIn(0.0f, 10.0f)
            }
            ImGui.popItemWidth()

            // Sky rotation
            val rotationArr = floatArrayOf(component.skyRotation)
            ImGui.pushItemWidth(120f)
            if (ImGui.dragFloat(
                    stringManager.getString("lbl.environment_system.sky_rotation"),
                    rotationArr,
                    1.0f,
                    0.0f,
                    360.0f,
                    "%.1f"
                )
            ) {
                component.skyRotation = rotationArr[0]
            }
            ImGui.popItemWidth()
        }

        ImGui.separator()

        // Fog configuration section
        if (ImGui.collapsingHeader(stringManager.getString("lbl.environment_system.fog_header"))) {
            // Render fog toggle
            val renderFog = ImBoolean(component.renderFog)
            if (ImGui.checkbox(stringManager.getString("lbl.environment_system.render_fog"), renderFog)) {
                component.renderFog = renderFog.get()
            }

            // Fog color
            val fogColorArr = floatArrayOf(component.fogColor.x, component.fogColor.y, component.fogColor.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment_system.fog_color"), fogColorArr)) {
                component.fogColor.set(fogColorArr[0], fogColorArr[1], fogColorArr[2])
            }

            // Fog density
            val densityArr = floatArrayOf(component.fogDensity)
            ImGui.pushItemWidth(120f)
            if (ImGui.dragFloat(
                    stringManager.getString("lbl.environment_system.fog_density"),
                    densityArr,
                    0.0001f,
                    0.0f,
                    1.0f,
                    "%.4f"
                )
            ) {
                component.fogDensity = densityArr[0].coerceIn(0.0f, 1.0f)
            }
            ImGui.popItemWidth()

            // Fog gradient
            val gradientArr = floatArrayOf(component.fogGradient)
            ImGui.pushItemWidth(120f)
            if (ImGui.dragFloat(
                    stringManager.getString("lbl.environment_system.fog_gradient"),
                    gradientArr,
                    0.1f,
                    0.1f,
                    10.0f,
                    "%.1f"
                )
            ) {
                component.fogGradient = gradientArr[0].coerceIn(0.1f, 10.0f)
            }
            ImGui.popItemWidth()
        }

        ImGui.separator()

        // Reset button
        if (ImGui.button(stringManager.getString("lbl.environment_system.reset_to_defaults"))) {
            reset()
        }
    }

    override fun invalidateCaches() {
        environmentComponent = null
    }
}
