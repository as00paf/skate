package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.systems.DayNightCycleSystem
import imgui.ImGui
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EnvironmentWindow : KoinComponent {
    private val stringManager: StringManager by inject()

    fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.environment"))

        // Find DayNightCycleSystem and DirectionalLightComponent
        val dayNightSystem = scene.systemManager.getSystem<DayNightCycleSystem>()
        val lightEntity = scene.gameObjectManager.gameObjects.find {
            it.getComponent<DirectionalLightComponent>() != null
        }
        val light = lightEntity?.getComponent<DirectionalLightComponent>()

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

        if (light != null && ImGui.collapsingHeader("${Icons.SUN} ${stringManager.getString("lbl.environment.sun")}")) {
            // Light direction
            val sunDir = floatArrayOf(light.direction.x, light.direction.y, light.direction.z)
            if (ImGui.dragFloat3(stringManager.getString("lbl.environment.sun_direction"), sunDir, 0.01f, -1f, 1f)) {
                light.direction.set(sunDir[0], sunDir[1], sunDir[2]).normalize()
            }

            // Light color
            val sunColor = floatArrayOf(light.color.x, light.color.y, light.color.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.sun_color"), sunColor)) {
                light.color.set(sunColor[0], sunColor[1], sunColor[2])
            }

            // Light intensity
            val sunIntensity = floatArrayOf(light.intensity)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.sun_intensity"), sunIntensity, 0.1f, 0f, 10f)) {
                light.intensity = sunIntensity[0]
            }

            ImGui.separator()
            ImGui.text("Shadow Settings")

            // Shadow distance
            val shadowDistance = floatArrayOf(light.shadowDistance)
            if (ImGui.dragFloat("Shadow Distance (m)", shadowDistance, 1f, 10f, 200f)) {
                light.shadowDistance = shadowDistance[0]
            }

            // Auto calculate bounds
            val autoBounds = ImBoolean(light.autoCalculateBounds)
            if (ImGui.checkbox("Auto Calculate Bounds", autoBounds)) {
                light.autoCalculateBounds = autoBounds.get()
            }

            // Stabilize projection
            val stabilize = ImBoolean(light.stabilizeProjection)
            if (ImGui.checkbox("Stabilize Projection", stabilize)) {
                light.stabilizeProjection = stabilize.get()
            }

            // Depth bias
            val depthBias = floatArrayOf(light.depthBias)
            if (ImGui.dragFloat("Depth Bias", depthBias, 0.0001f, 0f, 0.1f, "%.4f")) {
                light.depthBias = depthBias[0]
            }

            // Slope-scaled bias
            val slopeBias = floatArrayOf(light.slopeScaledBias)
            if (ImGui.dragFloat("Slope-Scaled Bias", slopeBias, 0.001f, 0f, 0.1f, "%.3f")) {
                light.slopeScaledBias = slopeBias[0]
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

            val ambient = floatArrayOf(scene.sceneData.ambientLight.x, scene.sceneData.ambientLight.y, scene.sceneData.ambientLight.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.ambient_light"), ambient)) {
                scene.sceneData.ambientLight.set(ambient[0], ambient[1], ambient[2])
            }
        }

        ImGui.end()
    }
}
