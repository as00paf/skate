package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.events.EnvironmentAction
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.AmbientLightComponent
import imgui.ImGui
import org.joml.Vector3f

fun AmbientLightComponent.imgui(stringManager: StringManager, eventSystem: EventSystem, logger: LoggerService) {
    // Light color
    val ambient = floatArrayOf(lightColor.x, lightColor.y, lightColor.z)
    if (MImGui.colorEdit3(stringManager.getString("lbl.environment.ambient_light"), ambient)) {
        eventSystem.publish(
            EnvironmentAction.SetAmbientLightRequested(
                ambientLightComponent = this,
                oldValue = Vector3f(lightColor),
                newValue = Vector3f(ambient[0], ambient[1], ambient[2]),
            )
        )
    }

    // Light intensity
    val ambientIntensityArr = floatArrayOf(intensity)
    ImGui.text(stringManager.getString("lbl.environment.ambient_intensity"))
    ImGui.sameLine()
    ImGui.pushItemWidth(ImGui.getContentRegionAvailX())
    if (ImGui.sliderFloat(
            "##ambient_light_intensity",
            ambientIntensityArr,
            0.0f,
            2.0f
        )
    ) {
        eventSystem.publish(
            EnvironmentAction.SetAmbientIntensityRequested(
                ambientLightComponent = this,
                oldValue = intensity,
                newValue = ambientIntensityArr[0].coerceIn(0.0f, 2.0f),
            )
        )
    }
    ImGui.popItemWidth()
}