package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.commands.EnvironmentPropertyCommand
import com.pafoid.skate.editor.commands.EnvironmentToggleCommand
import com.pafoid.skate.editor.imgui.IWindowWithScene
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import com.pafoid.skate.engine.ecs.systems.EnvironmentSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import imgui.ImGui
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class EnvironmentWindow(
    private val stringManager: StringManager,
    private val undoRedoManager: UndoRedoManager,
    private val systemManager: SystemManager,
) : IWindowWithScene, KoinComponent {

    override fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.environment"))

        val dayNight = scene.getComponent<DayNightCycleComponent>()
        val lightSystem = systemManager.getSystem<DirectionalLightSystem>()
        val environmentSystem = systemManager.getSystem<EnvironmentSystem>()

        val timeComponent = scene.getComponent<TimeComponent>() ?: TimeComponent()
        val lightingStateComponent = scene.getComponent<LightingStateComponent>() ?: LightingStateComponent()

        if (ImGui.collapsingHeader("${Icons.GEAR} ${stringManager.getString("lbl.environment.time_of_day")}")) {
            val cycleTime = dayNight?.cycleTime ?: timeComponent.timeOfDay
            val time = floatArrayOf(cycleTime)
            val hours = time[0].toInt()
            val minutes = ((time[0] - hours) * 60).toInt()
            val timeString = String.format("%02d:%02d", hours, minutes)

            if (ImGui.sliderFloat(stringManager.getString("lbl.environment.time"), time, 0f, 24f, timeString)) {
                val oldTime = timeComponent.timeOfDay
                val newTime = time[0]
                undoRedoManager.executeCommand(
                    EnvironmentPropertyCommand(
                        displayName = "Set Time of Day",
                        targetName = null,
                        setter = { t -> 
                            timeComponent.timeOfDay = t
                            dayNight?.cycleTime = t
                        },
                        oldValue = oldTime,
                        newValue = newTime
                    )
                )
                if (!scene.hasComponent<TimeComponent>()) {
                    scene.addComponent(timeComponent)
                }
            }
        }

        environmentSystem?.let { system ->
            if (ImGui.collapsingHeader("${Icons.PALETTE} ${stringManager.getString("lbl.environment_system.header")}")) {
                system.imgui()
            }
        }

        val config = lightSystem?.config
        if (config != null && ImGui.collapsingHeader("${Icons.SUN} ${stringManager.getString("lbl.environment.sun")}")) {
            val sunDir = floatArrayOf(config.direction.x, config.direction.y, config.direction.z)
            if (MImGui.dragFloat3(stringManager.getString("lbl.environment.sun_direction"), sunDir, 0.01f)) {
                config.direction.set(sunDir[0], sunDir[1], sunDir[2]).normalize()
            }

            val sunColor = floatArrayOf(config.color.x, config.color.y, config.color.z)
            if (MImGui.colorEdit3(stringManager.getString("lbl.environment.sun_color"), sunColor)) {
                config.color.set(sunColor[0], sunColor[1], sunColor[2])
            }

            val sunIntensity = floatArrayOf(config.intensity)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.sun_intensity"), sunIntensity, 0.1f, 0f, 10f)) {
                config.intensity = sunIntensity[0]
            }

            ImGui.separator()
            MImGui.textDisabled(stringManager.getString("lbl.environment.shadow_settings"))

            val shadowDistance = floatArrayOf(config.shadowDistance)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.shadow_distance"), shadowDistance, 1f, 10f, 200f)) {
                config.shadowDistance = shadowDistance[0]
            }

            val autoBounds = ImBoolean(config.autoCalculateBounds)
            if (ImGui.checkbox(stringManager.getString("lbl.environment.auto_calculate_bounds"), autoBounds)) {
                config.autoCalculateBounds = autoBounds.get()
            }

            val stabilize = ImBoolean(config.stabilizeProjection)
            if (ImGui.checkbox(stringManager.getString("lbl.environment.stabilize_projection"), stabilize)) {
                config.stabilizeProjection = stabilize.get()
            }

            val depthBias = floatArrayOf(config.depthBias)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.depth_bias"), depthBias, 0.0001f, 0f, 0.1f, "%.4f")) {
                config.depthBias = depthBias[0]
            }

            val slopeBias = floatArrayOf(config.slopeScaledBias)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.slope_scaled_bias"), slopeBias, 0.001f, 0f, 0.1f, "%.3f")) {
                config.slopeScaledBias = slopeBias[0]
            }
        }

        if (ImGui.collapsingHeader("${Icons.PALETTE} ${stringManager.getString("lbl.environment.lighting")}")) {
            val useAmbient = ImBoolean(lightingStateComponent.useAmbient)
            if (ImGui.checkbox(stringManager.getString("lbl.environment.use_ambient"), useAmbient)) {
                val oldVal = lightingStateComponent.useAmbient
                val newVal = useAmbient.get()
                undoRedoManager.executeCommand(
                    EnvironmentToggleCommand(
                        displayName = "Toggle Use Ambient",
                        setter = { v -> lightingStateComponent.useAmbient = v },
                        oldValue = oldVal,
                        newValue = newVal
                    )
                )
                if (!scene.hasComponent<LightingStateComponent>()) {
                    scene.addComponent(lightingStateComponent)
                }
            }

            dayNight?.let { dayNight ->
                val autoAmbient = ImBoolean(dayNight.autoAmbient)
                if (ImGui.checkbox(stringManager.getString("lbl.environment.auto_ambient"), autoAmbient)) {
                    val oldVal = dayNight.autoAmbient
                    val newVal = autoAmbient.get()
                    undoRedoManager.executeCommand(
                        EnvironmentToggleCommand(
                            displayName = "Toggle Auto Ambient",
                            setter = { v -> dayNight.autoAmbient = v },
                            oldValue = oldVal,
                            newValue = newVal
                        )
                    )
                }
            }

            val autoAmbientEnabled = dayNight?.autoAmbient ?: true
            if (autoAmbientEnabled) {
                // Show computed ambient (read-only display)
                val computedAmbient = dayNight?.ambientColor ?: lightingStateComponent.ambientLight
                ImGui.text(
                    stringManager.getString("lbl.environment.ambient_auto").format(
                        computedAmbient.x,
                        computedAmbient.y,
                        computedAmbient.z
                    )
                )
            } else {
                val ambient = floatArrayOf(
                    lightingStateComponent.ambientLight.x,
                    lightingStateComponent.ambientLight.y,
                    lightingStateComponent.ambientLight.z
                )
                if (MImGui.colorEdit3(stringManager.getString("lbl.environment.ambient_light"), ambient)) {
                    lightingStateComponent.ambientLight.set(ambient[0], ambient[1], ambient[2])
                }
            }

            dayNight?.let { dayNight ->
                val ambientIntensityArr = floatArrayOf(dayNight.ambientIntensity)
                if (ImGui.sliderFloat(stringManager.getString("lbl.environment.ambient_intensity"), ambientIntensityArr, 0.0f, 2.0f)) {
                    dayNight.ambientIntensity = ambientIntensityArr[0].coerceIn(0.0f, 2.0f)
                }
            }
        }

        ImGui.end()
    }
}
