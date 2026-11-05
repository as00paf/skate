package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import imgui.ImGui
import imgui.type.ImString
import org.joml.Vector4f

// Colors get pickers below; the runtime holds live simulation state, not settings.
private val CUSTOM_FIELDS = setOf("startColor", "endColor", "texturePath", "runtime")

fun ParticleEmitter.imgui(engine: Engine) {
    val strings = engine.stringManager
    fun s(key: String) = strings.getString("component.ParticleEmitter.$key")
    ImGui.pushID(System.identityHashCode(this))

    reflectedImgui(engine, skip = CUSTOM_FIELDS)
    colorEdit(s("startColor"), startColor)
    Tooltips.showIfPresent(strings, "component.ParticleEmitter.startColor.tooltip")
    colorEdit(s("endColor"), endColor)
    Tooltips.showIfPresent(strings, "component.ParticleEmitter.endColor.tooltip")

    val path = ImString(texturePath, 512)
    if (ImGui.inputText(s("texturePath"), path)) texturePath = path.get().trim()
    Tooltips.showIfPresent(strings, "component.ParticleEmitter.texturePath.tooltip")

    val scene = engine.sceneManager.currentScene
    if (scene?.isRunning == true) {
        ImGui.text(strings.getString("component.ParticleEmitter.liveCount", runtime.pool.count))
        if (strings.tipped(ImGui.button(s("restart")), "component.ParticleEmitter.restart")) play()
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(s("stop")), "component.ParticleEmitter.stop")) stop()
    }
    ImGui.popID()
}

private fun colorEdit(label: String, color: Vector4f) {
    val rgba = floatArrayOf(color.x, color.y, color.z, color.w)
    if (ImGui.colorEdit4(label, rgba)) color.set(rgba[0], rgba[1], rgba[2], rgba[3])
}
