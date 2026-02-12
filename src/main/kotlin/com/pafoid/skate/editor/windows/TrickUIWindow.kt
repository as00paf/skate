package com.pafoid.skate.editor.windows

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.game.trick.TrickDetector
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags

class TrickUIWindow {
    private var trickGameObject: GameObject? = null

    fun setTrickGameObject(go: GameObject?) {
        trickGameObject = go
    }

    fun imgui(xPos: Float, yPos: Float, width: Float, height: Float) {
        val detectedTrick = trickGameObject?.getComponent<TrickDetector>()?.getDetectedTrick() ?: "No trick detected"

        ImGui.setCursorPos(xPos, yPos)
        ImGui.beginChild("##TrickOverlay", width, height, false, ImGuiWindowFlags.NoDecoration or ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoSavedSettings or ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoInputs)
        ImGui.setWindowFontScale(1.5f)
        ImGui.textColored(1f, 0.5f, 0f, 1f, detectedTrick)
        ImGui.endChild()
    }
}
