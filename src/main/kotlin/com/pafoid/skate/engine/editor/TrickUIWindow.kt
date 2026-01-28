package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.components.TrickDetector
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import org.joml.Vector2f

class TrickUIWindow {
    private var trickGameObject: GameObject? = null

    fun setTrickGameObject(go: GameObject?) {
        trickGameObject = go
    }

    fun imgui(xPos: Float, yPos: Float, width: Float, height: Float) {
        val detectedTrick = trickGameObject?.getComponent<TrickDetector>()?.getDetectedTrick()

        if (detectedTrick != null && detectedTrick.isNotBlank()) {
            ImGui.setCursorPos(xPos, yPos)
            ImGui.beginChild("##TrickOverlay", width, height, false, ImGuiWindowFlags.NoDecoration or ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoSavedSettings or ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoInputs)
            ImGui.setWindowFontScale(1.5f)
            ImGui.textColored(1f, 0.5f, 0f, 1f, detectedTrick)
            ImGui.endChild()
        }
    }
}
