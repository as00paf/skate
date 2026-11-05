package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.AmbientLightComponent

/** Written live; InspectorEditTracker makes each finished drag one undoable edit (EDT-10). */
fun AmbientLightComponent.imgui(engine: Engine) {
    val ambient = floatArrayOf(lightColor.x, lightColor.y, lightColor.z)
    if (MImGui.colorEdit3(engine.stringManager.getString("lbl.environment.ambient_light"), ambient)) {
        lightColor.set(ambient[0], ambient[1], ambient[2])
    }
    intensity = MImGui.sliderFloat(intensity, engine.stringManager.getString("lbl.environment.ambient_intensity"))
}
