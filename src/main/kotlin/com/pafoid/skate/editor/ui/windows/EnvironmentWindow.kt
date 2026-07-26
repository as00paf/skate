package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.EnvironmentAction
import com.pafoid.skate.editor.imgui.IWindowWithScene
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.systems.imgui
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import com.pafoid.skate.engine.ecs.systems.EnvironmentSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.type.ImBoolean
import org.joml.Vector3f

class EnvironmentWindow(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
    private val systemManager: SystemManager
) : IWindowWithScene {
    override fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.environment"))

        val dayNight = scene.getComponent<DayNightCycleComponent>()
        val lightSystem = systemManager.getSystem<DirectionalLightSystem>()
        val environmentSystem = systemManager.getSystem<EnvironmentSystem>()
        val lightingStateComponent = scene.getComponent<LightingStateComponent>() ?: LightingStateComponent()

        if (dayNight != null) {
            if (ImGui.collapsingHeader("${Icons.GEAR} ${stringManager.getString("lbl.environment.time_of_day")}")) {
                val cycleTime = dayNight.timeOfDay
                val time = floatArrayOf(cycleTime)
                val hours = time[0].toInt()
                val minutes = ((time[0] - hours) * 60).toInt()
                val timeString = String.format("%02d:%02d", hours, minutes)

                if (ImGui.sliderFloat(stringManager.getString("lbl.environment.time"), time, 0f, 24f, timeString)) {
                    val oldTime = dayNight.timeOfDay
                    val newTime = time[0]
                    eventSystem.publish(
                        EnvironmentAction.SetTimeOfDayRequested(
                            scene = scene,
                            dayNightCycle = dayNight,
                            oldTime = oldTime,
                            newTime = newTime
                        )
                    )
                }
            }
        }

        environmentSystem?.let { system ->
            if (ImGui.collapsingHeader("${Icons.PALETTE} ${stringManager.getString("lbl.environment_system.header")}")) {
                system.imgui(stringManager)
            }
        }

        val config = lightSystem?.config
        if (config != null) {
            if (ImGui.collapsingHeader("${Icons.SUN} ${stringManager.getString("lbl.environment.sun")}")) {
                val sunDir = floatArrayOf(config.direction.x, config.direction.y, config.direction.z)
                if (MImGui.dragFloat3(stringManager.getString("lbl.environment.sun_direction"), sunDir, 0.01f)) {
                    eventSystem.publish(
                        EnvironmentAction.SetSunDirectionRequested(
                            lightConfig = config,
                            oldValue = Vector3f(config.direction),
                            newValue = Vector3f(sunDir[0], sunDir[1], sunDir[2]).normalize(),
                        )
                    )
                }

                val sunColor = floatArrayOf(config.color.x, config.color.y, config.color.z)
                if (MImGui.colorEdit3(stringManager.getString("lbl.environment.sun_color"), sunColor)) {
                    eventSystem.publish(
                        EnvironmentAction.SetSunColorRequested(
                            lightConfig = config,
                            oldValue = Vector3f(config.color),
                            newValue = Vector3f(sunColor[0], sunColor[1], sunColor[2]),
                        )
                    )
                }

                val sunIntensity = floatArrayOf(config.intensity)
                if (ImGui.dragFloat(
                        stringManager.getString("lbl.environment.sun_intensity"),
                        sunIntensity,
                        0.1f,
                        0f,
                        10f
                    )
                ) {
                    eventSystem.publish(
                        EnvironmentAction.SetSunIntensityRequested(
                            lightConfig = config,
                            oldValue = config.intensity,
                            newValue = sunIntensity[0],
                        )
                    )
                }

                ImGui.separator()
                MImGui.textDisabled(stringManager.getString("lbl.environment.shadow_settings"))

                val shadowDistance = floatArrayOf(config.shadowDistance)
                if (ImGui.dragFloat(
                        stringManager.getString("lbl.environment.shadow_distance"),
                        shadowDistance,
                        1f,
                        10f,
                        200f
                    )
                ) {
                    eventSystem.publish(
                        EnvironmentAction.SetShadowDistanceRequested(
                            lightConfig = config,
                            oldValue = config.shadowDistance,
                            newValue = shadowDistance[0],
                        )
                    )
                }

                val autoBounds = ImBoolean(config.autoCalculateBounds)
                if (ImGui.checkbox(stringManager.getString("lbl.environment.auto_calculate_bounds"), autoBounds)) {
                    eventSystem.publish(
                        EnvironmentAction.SetAutoCalculateBoundsRequested(
                            lightConfig = config,
                            oldValue = config.autoCalculateBounds,
                            newValue = autoBounds.get(),
                        )
                    )
                }

                val stabilize = ImBoolean(config.stabilizeProjection)
                if (ImGui.checkbox(stringManager.getString("lbl.environment.stabilize_projection"), stabilize)) {
                    eventSystem.publish(
                        EnvironmentAction.SetStabilizeProjectionRequested(
                            lightConfig = config,
                            oldValue = config.stabilizeProjection,
                            newValue = stabilize.get(),
                        )
                    )
                }

                val depthBias = floatArrayOf(config.depthBias)
                if (ImGui.dragFloat(
                        stringManager.getString("lbl.environment.depth_bias"),
                        depthBias,
                        0.0001f,
                        0f,
                        0.1f,
                        "%.4f"
                    )
                ) {
                    eventSystem.publish(
                        EnvironmentAction.SetDepthBiasRequested(
                            lightConfig = config,
                            oldValue = config.depthBias,
                            newValue = depthBias[0],
                        )
                    )
                }

                val slopeBias = floatArrayOf(config.slopeScaledBias)
                if (ImGui.dragFloat(
                        stringManager.getString("lbl.environment.slope_scaled_bias"),
                        slopeBias,
                        0.001f,
                        0f,
                        0.1f,
                        "%.3f"
                    )
                ) {
                    eventSystem.publish(
                        EnvironmentAction.SetSlopeScaledBiasRequested(
                            lightConfig = config,
                            oldValue = config.slopeScaledBias,
                            newValue = slopeBias[0],
                        )
                    )
                }
            }
        }

        if (ImGui.collapsingHeader("${Icons.PALETTE} ${stringManager.getString("lbl.environment.lighting")}")) {
            val useAmbient = ImBoolean(lightingStateComponent.useAmbient)
            if (ImGui.checkbox(stringManager.getString("lbl.environment.use_ambient"), useAmbient)) {
                val oldVal = lightingStateComponent.useAmbient
                val newVal = useAmbient.get()
                eventSystem.publish(
                    EnvironmentAction.SetUseAmbientRequested(
                        scene = scene,
                        lightingStateComponent = lightingStateComponent,
                        oldValue = oldVal,
                        newValue = newVal
                    )
                )
            }

            dayNight?.let { dayNight ->
                val autoAmbient = ImBoolean(dayNight.autoAmbient)
                if (ImGui.checkbox(stringManager.getString("lbl.environment.auto_ambient"), autoAmbient)) {
                    val oldVal = dayNight.autoAmbient
                    val newVal = autoAmbient.get()
                    eventSystem.publish(
                        EnvironmentAction.SetAutoAmbientRequested(
                            dayNightCycle = dayNight,
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
                    eventSystem.publish(
                        EnvironmentAction.SetAmbientLightRequested(
                            lightingStateComponent = lightingStateComponent,
                            oldValue = Vector3f(lightingStateComponent.ambientLight),
                            newValue = Vector3f(ambient[0], ambient[1], ambient[2]),
                        )
                    )
                }
            }

            dayNight?.let { dayNight ->
                val ambientIntensityArr = floatArrayOf(dayNight.ambientIntensity)
                if (ImGui.sliderFloat(stringManager.getString("lbl.environment.ambient_intensity"), ambientIntensityArr, 0.0f, 2.0f)) {
                    eventSystem.publish(
                        EnvironmentAction.SetAmbientIntensityRequested(
                            dayNightCycle = dayNight,
                            oldValue = dayNight.ambientIntensity,
                            newValue = ambientIntensityArr[0].coerceIn(0.0f, 2.0f),
                        )
                    )
                }
            }
        }

        ImGui.end()
    }
}
