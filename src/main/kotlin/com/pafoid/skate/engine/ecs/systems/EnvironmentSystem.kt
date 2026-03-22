package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.config.EnvironmentConfig
import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import imgui.ImGui
import imgui.type.ImBoolean

/**
 * System responsible for managing environment settings.
 *
 * This system runs at [ExecutionPriority.EARLY] to ensure environment state
 * is ready before rendering systems read from [config].
 *
 * ## Responsibilities
 *
 * - Owns [EnvironmentConfig] with all sky and fog settings
 * - Provides ImGui interface for real-time environment editing
 * - Supports environment presets for quick configuration
 * - Integrates with DayNightCycleSystem for coordinated lighting
 *
 * ## Configuration
 *
 * - [skyColor]: Clear sky color (used for clear color and sky dome)
 * - [skyTint]: Sky color tint multiplier
 * - [skyExposure]: Sky exposure/brightness
 * - [skyRotation]: Sky rotation in degrees
 * - [fogColor]: Fog color
 * - [fogDensity]: Fog density (0 = no fog)
 * - [fogGradient]: Fog gradient falloff
 *
 * ## Usage
 *
 * ```kotlin
 * val environmentSystem = EnvironmentSystem(stringManager)
 * scene.addSystem(environmentSystem)
 *
 * // Access config from other systems
 * val envConfig = environmentSystem.config
 * envConfig.fogDensity = 0.01f
 * ```
 *
 * @param stringManager String manager for localized UI strings
 */
class EnvironmentSystem(
    initialConfig: EnvironmentConfig = EnvironmentConfig(),
    private val stringManager: StringManager
) : System(priority = ExecutionPriority.EARLY) {

    // System-owned configuration
    val config = initialConfig

    /**
     * Applies an environment preset.
     *
     * @param preset The preset to apply
     */
    fun applyPreset(preset: EnvironmentPreset) {
        config.applyPreset(preset)
    }

    /**
     * Resets configuration to defaults.
     */
    fun reset() {
        config.reset()
    }

    override fun imgui() {
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
            val renderSky = ImBoolean(config.renderSky)
            if (ImGui.checkbox(stringManager.getString("lbl.environment_system.render_sky"), renderSky)) {
                config.renderSky = renderSky.get()
            }

            // Sky color
            val skyColorArr = floatArrayOf(config.skyColor.x, config.skyColor.y, config.skyColor.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment_system.sky_color"), skyColorArr)) {
                config.skyColor.set(skyColorArr[0], skyColorArr[1], skyColorArr[2])
            }

            // Sky tint
            val skyTintArr = floatArrayOf(config.skyTint.x, config.skyTint.y, config.skyTint.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment_system.sky_tint"), skyTintArr)) {
                config.skyTint.set(skyTintArr[0], skyTintArr[1], skyTintArr[2])
            }

            // Sky exposure
            val exposureArr = floatArrayOf(config.skyExposure)
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
                config.skyExposure = exposureArr[0].coerceIn(0.0f, 10.0f)
            }
            ImGui.popItemWidth()

            // Sky rotation
            val rotationArr = floatArrayOf(config.skyRotation)
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
                config.skyRotation = rotationArr[0]
            }
            ImGui.popItemWidth()
        }

        ImGui.separator()

        // Fog configuration section
        if (ImGui.collapsingHeader(stringManager.getString("lbl.environment_system.fog_header"))) {
            // Render fog toggle
            val renderFog = ImBoolean(config.renderFog)
            if (ImGui.checkbox(stringManager.getString("lbl.environment_system.render_fog"), renderFog)) {
                config.renderFog = renderFog.get()
            }

            // Fog color
            val fogColorArr = floatArrayOf(config.fogColor.x, config.fogColor.y, config.fogColor.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment_system.fog_color"), fogColorArr)) {
                config.fogColor.set(fogColorArr[0], fogColorArr[1], fogColorArr[2])
            }

            // Fog density
            val densityArr = floatArrayOf(config.fogDensity)
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
                config.fogDensity = densityArr[0].coerceIn(0.0f, 1.0f)
            }
            ImGui.popItemWidth()

            // Fog gradient
            val gradientArr = floatArrayOf(config.fogGradient)
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
                config.fogGradient = gradientArr[0].coerceIn(0.1f, 10.0f)
            }
            ImGui.popItemWidth()
        }

        ImGui.separator()

        // Reset button
        if (ImGui.button(stringManager.getString("lbl.environment_system.reset_to_defaults"))) {
            reset()
        }
    }
}
