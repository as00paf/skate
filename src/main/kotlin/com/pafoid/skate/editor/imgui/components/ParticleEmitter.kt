package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
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
    colorEdit(strings, "startColor", startColor)
    colorEdit(strings, "endColor", endColor)

    val path = ImString(texturePath, 512)
    if (MImGui.field(s("texturePath"), "texturePath") {
            ImGui.inputText("##value", path).also { Tooltips.showIfPresent(strings, "component.ParticleEmitter.texturePath.tooltip") }
        }
    ) texturePath = path.get().trim()

    val scene = engine.sceneManager.currentScene
    if (scene?.isRunning == true) {
        ImGui.text(strings.getString("component.ParticleEmitter.liveCount", runtime.pool.count))
        if (strings.tipped(ImGui.button(s("restart")), "component.ParticleEmitter.restart")) play()
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(s("stop")), "component.ParticleEmitter.stop")) stop()
    }
    ImGui.popID()
}

private fun colorEdit(strings: com.pafoid.skate.engine.core.StringManager, field: String, color: Vector4f) {
    val rgba = floatArrayOf(color.x, color.y, color.z, color.w)
    val changed = MImGui.field(strings.getString("component.ParticleEmitter.$field"), field) {
        ImGui.colorEdit4("##value", rgba).also { Tooltips.showIfPresent(strings, "component.ParticleEmitter.$field.tooltip") }
    }
    if (changed) color.set(rgba[0], rgba[1], rgba[2], rgba[3])
}
