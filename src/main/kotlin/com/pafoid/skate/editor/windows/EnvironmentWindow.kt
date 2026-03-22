package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindowWithScene
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.ecs.systems.DayNightCycleSystem
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import com.pafoid.skate.engine.ecs.systems.EnvironmentSystem
import imgui.ImGui
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Environment editor window.
 *
 * This window provides controls for:
 * - Time of day (via TimeComponent and DayNightCycleSystem)
 * - Environment settings (via EnvironmentSystem)
 * - Directional light and shadows (via DirectionalLightSystem)
 * - Ambient lighting (via LightingStateComponent and DayNightCycleSystem)
 */
class EnvironmentWindow : IWindowWithScene, KoinComponent {
    private val stringManager: StringManager by inject()

    override fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.environment"))

        // Get systems
        val dayNightSystem = scene.systemManager.getSystem<DayNightCycleSystem>()
        val lightSystem = scene.systemManager.getSystem<DirectionalLightSystem>()
        val environmentSystem = scene.systemManager.getSystem<EnvironmentSystem>()

        // Get scene components
        val timeComponent = scene.getComponent<TimeComponent>() ?: TimeComponent()
        val lightingStateComponent = scene.getComponent<LightingStateComponent>() ?: LightingStateComponent()

        if (ImGui.collapsingHeader("${Icons.GEAR} ${stringManager.getString("lbl.environment.time_of_day")}")) {
            // Sync with DayNightCycleSystem if available
            val cycleTime = dayNightSystem?.getCycleTime() ?: timeComponent.timeOfDay
            val time = floatArrayOf(cycleTime)
            val hours = time[0].toInt()
            val minutes = ((time[0] - hours) * 60).toInt()
            val timeString = String.format("%02d:%02d", hours, minutes)

            if (ImGui.sliderFloat(stringManager.getString("lbl.environment.time"), time, 0f, 24f, timeString)) {
                timeComponent.timeOfDay = time[0]
                // Ensure component is on scene
                if (!scene.hasComponent<TimeComponent>()) {
                    scene.addComponent(timeComponent)
                }
                dayNightSystem?.setCycleTime(time[0])
            }
        }

        // Environment settings - delegated to EnvironmentSystem
        environmentSystem?.let { system ->
            if (ImGui.collapsingHeader("${Icons.PALETTE} ${stringManager.getString("lbl.environment_system.header")}")) {
                system.imgui()
            }
        }

        if (lightSystem != null && ImGui.collapsingHeader("${Icons.SUN} ${stringManager.getString("lbl.environment.sun")}")) {
            val config = lightSystem.config

            // Light direction
            val sunDir = floatArrayOf(config.direction.x, config.direction.y, config.direction.z)
            if (ImGui.dragFloat3(stringManager.getString("lbl.environment.sun_direction"), sunDir, 0.01f, -1f, 1f)) {
                config.direction.set(sunDir[0], sunDir[1], sunDir[2]).normalize()
            }

            // Light color
            val sunColor = floatArrayOf(config.color.x, config.color.y, config.color.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.sun_color"), sunColor)) {
                config.color.set(sunColor[0], sunColor[1], sunColor[2])
            }

            // Light intensity
            val sunIntensity = floatArrayOf(config.intensity)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.sun_intensity"), sunIntensity, 0.1f, 0f, 10f)) {
                config.intensity = sunIntensity[0]
            }

            ImGui.separator()
            ImGui.text("Shadow Settings")

            // Shadow distance
            val shadowDistance = floatArrayOf(config.shadowDistance)
            if (ImGui.dragFloat("Shadow Distance (m)", shadowDistance, 1f, 10f, 200f)) {
                config.shadowDistance = shadowDistance[0]
            }

            // Auto calculate bounds
            val autoBounds = ImBoolean(config.autoCalculateBounds)
            if (ImGui.checkbox("Auto Calculate Bounds", autoBounds)) {
                config.autoCalculateBounds = autoBounds.get()
            }

            // Stabilize projection
            val stabilize = ImBoolean(config.stabilizeProjection)
            if (ImGui.checkbox("Stabilize Projection", stabilize)) {
                config.stabilizeProjection = stabilize.get()
            }

            // Depth bias
            val depthBias = floatArrayOf(config.depthBias)
            if (ImGui.dragFloat("Depth Bias", depthBias, 0.0001f, 0f, 0.1f, "%.4f")) {
                config.depthBias = depthBias[0]
            }

            // Slope-scaled bias
            val slopeBias = floatArrayOf(config.slopeScaledBias)
            if (ImGui.dragFloat("Slope-Scaled Bias", slopeBias, 0.001f, 0f, 0.1f, "%.3f")) {
                config.slopeScaledBias = slopeBias[0]
            }
        }

        if (ImGui.collapsingHeader("${Icons.PALETTE} ${stringManager.getString("lbl.environment.lighting")}")) {
            val useAmbient = ImBoolean(lightingStateComponent.useAmbient)
            if (ImGui.checkbox(stringManager.getString("lbl.environment.use_ambient"), useAmbient)) {
                lightingStateComponent.useAmbient = useAmbient.get()
                // Ensure component is on scene
                if (!scene.hasComponent<LightingStateComponent>()) {
                    scene.addComponent(lightingStateComponent)
                }
            }

            // Auto Ambient toggle (only when DayNightCycleSystem exists)
            dayNightSystem?.let { system ->
                val autoAmbient = ImBoolean(system.config.autoAmbient)
                if (ImGui.checkbox("Auto Ambient (Day/Night Cycle)", autoAmbient)) {
                    system.config.autoAmbient = autoAmbient.get()
                }
            }

            // Ambient color picker - disabled when auto ambient is enabled
            val autoAmbientEnabled = dayNightSystem?.config?.autoAmbient ?: true
            if (autoAmbientEnabled) {
                // Show computed ambient (read-only display)
                val computedAmbient = dayNightSystem?.config?.ambientColor ?: lightingStateComponent.ambientLight
                ImGui.text(
                    "Ambient (Auto): (%.2f, %.2f, %.2f)".format(
                        computedAmbient.x,
                        computedAmbient.y,
                        computedAmbient.z
                    )
                )
            } else {
                // Manual control enabled
                val ambient = floatArrayOf(
                    lightingStateComponent.ambientLight.x,
                    lightingStateComponent.ambientLight.y,
                    lightingStateComponent.ambientLight.z
                )
                if (ImGui.colorEdit3(stringManager.getString("lbl.environment.ambient_light"), ambient)) {
                    lightingStateComponent.ambientLight.set(ambient[0], ambient[1], ambient[2])
                }
            }

            // Ambient intensity slider
            dayNightSystem?.let { system ->
                val ambientIntensityArr = floatArrayOf(system.config.ambientIntensity)
                if (ImGui.sliderFloat("Ambient Intensity", ambientIntensityArr, 0.0f, 2.0f)) {
                    system.config.ambientIntensity = ambientIntensityArr[0].coerceIn(0.0f, 2.0f)
                }
            }
        }

        ImGui.end()
    }
}
