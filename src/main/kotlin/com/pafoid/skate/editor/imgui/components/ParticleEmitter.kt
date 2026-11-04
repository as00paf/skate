package com.pafoid.skate.editor.imgui.components

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
    colorEdit(s("endColor"), endColor)

    val path = ImString(texturePath, 512)
    if (ImGui.inputText(s("texturePath"), path)) texturePath = path.get().trim()

    val scene = engine.sceneManager.currentScene
    if (scene?.isRunning == true) {
        ImGui.text(strings.getString("component.ParticleEmitter.liveCount", runtime.pool.count))
        if (ImGui.button(s("restart"))) play()
        ImGui.sameLine()
        if (ImGui.button(s("stop"))) stop()
    }
    ImGui.popID()
}

private fun colorEdit(label: String, color: Vector4f) {
    val rgba = floatArrayOf(color.x, color.y, color.z, color.w)
    if (ImGui.colorEdit4(label, rgba)) color.set(rgba[0], rgba[1], rgba[2], rgba[3])
}
