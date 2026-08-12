package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.events.EnvironmentAction
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.AmbientLightComponent
import org.joml.Vector3f

fun AmbientLightComponent.imgui(engine: Engine) {
    // Light color
    val ambient = floatArrayOf(lightColor.x, lightColor.y, lightColor.z)
    if (MImGui.colorEdit3(engine.stringManager.getString("lbl.environment.ambient_light"), ambient)) {
        engine.eventSystem.publish(
            EnvironmentAction.SetAmbientLightRequested(
                ambientLightComponent = this,
                oldValue = Vector3f(lightColor),
                newValue = Vector3f(ambient[0], ambient[1], ambient[2]),
            )
        )
    }

    // Light intensity
    intensity = MImGui.sliderFloat(intensity, engine.stringManager.getString("lbl.environment.ambient_intensity"))

    /*eventSystem.publish(
        EnvironmentAction.SetAmbientIntensityRequested(
            ambientLightComponent = this,
            oldValue = intensity,
            newValue = ambientIntensityArr[0].coerceIn(0.0f, 2.0f),
        )
    )*/

}