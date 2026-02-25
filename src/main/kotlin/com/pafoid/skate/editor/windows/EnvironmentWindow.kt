package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.DayNightCycleSystem
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import imgui.ImGui
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EnvironmentWindow : KoinComponent {
    private val stringManager: StringManager by inject()

    fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.environment"))

        // Get systems
        val dayNightSystem = scene.systemManager.getSystem<DayNightCycleSystem>()
        val lightSystem = scene.systemManager.getSystem<DirectionalLightSystem>()

        if (ImGui.collapsingHeader("${Icons.GEAR} ${stringManager.getString("lbl.environment.time_of_day")}")) {
            // Sync with DayNightCycleSystem if available
            val cycleTime = dayNightSystem?.getCycleTime() ?: scene.sceneData.timeOfDay
            val time = floatArrayOf(cycleTime)
            val hours = time[0].toInt()
            val minutes = ((time[0] - hours) * 60).toInt()
            val timeString = String.format("%02d:%02d", hours, minutes)

            if (ImGui.sliderFloat(stringManager.getString("lbl.environment.time"), time, 0f, 24f, timeString)) {
                scene.sceneData.timeOfDay = time[0]
                dayNightSystem?.setCycleTime(time[0])
            }
        }

        if (ImGui.collapsingHeader("${Icons.PALETTE} ${stringManager.getString("lbl.environment.atmosphere")}")) {
            val skyColor = floatArrayOf(scene.sceneData.skyColor.x, scene.sceneData.skyColor.y, scene.sceneData.skyColor.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.sky_color"), skyColor)) {
                scene.sceneData.skyColor.set(skyColor[0], skyColor[1], skyColor[2])
            }

            val skyTint = floatArrayOf(scene.sceneData.skyTint.x, scene.sceneData.skyTint.y, scene.sceneData.skyTint.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.sky_tint"), skyTint)) {
                scene.sceneData.skyTint.set(skyTint[0], skyTint[1], skyTint[2])
            }

            val exposure = floatArrayOf(scene.sceneData.skyExposure)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.sky_exposure"), exposure, 0.01f, 0f, 10f)) {
                scene.sceneData.skyExposure = exposure[0]
            }

            val skyRot = floatArrayOf(scene.sceneData.skyRotation)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.sky_rotation"), skyRot, 0.1f, 0f, 360f)) {
                scene.sceneData.skyRotation = skyRot[0]
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

        if (ImGui.collapsingHeader("${Icons.CLOUD} ${stringManager.getString("lbl.environment.fog")}")) {
            val fogColor = floatArrayOf(scene.sceneData.fogColor.x, scene.sceneData.fogColor.y, scene.sceneData.fogColor.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.fog_color"), fogColor)) {
                scene.sceneData.fogColor.set(fogColor[0], fogColor[1], fogColor[2])
            }

            val fogDensity = floatArrayOf(scene.sceneData.fogDensity)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.fog_density"), fogDensity, 0.0001f, 0f, 0.1f, "%.4f")) {
                scene.sceneData.fogDensity = fogDensity[0]
            }

            val fogGradient = floatArrayOf(scene.sceneData.fogGradient)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.fog_gradient"), fogGradient, 0.1f, 0.1f, 10f)) {
                scene.sceneData.fogGradient = fogGradient[0]
            }
        }

        if (ImGui.collapsingHeader("${Icons.PALETTE} ${stringManager.getString("lbl.environment.lighting")}")) {
            val useAmbient = ImBoolean(scene.sceneData.useAmbient)
            if (ImGui.checkbox(stringManager.getString("lbl.environment.use_ambient"), useAmbient)) {
                scene.sceneData.useAmbient = useAmbient.get()
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
                val computedAmbient = dayNightSystem?.config?.ambientColor ?: scene.sceneData.ambientLight
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
                    scene.sceneData.ambientLight.x,
                    scene.sceneData.ambientLight.y,
                    scene.sceneData.ambientLight.z
                )
                if (ImGui.colorEdit3(stringManager.getString("lbl.environment.ambient_light"), ambient)) {
                    scene.sceneData.ambientLight.set(ambient[0], ambient[1], ambient[2])
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
