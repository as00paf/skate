package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.controls.JoystickListener
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1

class GamepadOverlay {
    fun imgui() {
        val windowFlags = ImGuiWindowFlags.NoDecoration or 
                         ImGuiWindowFlags.NoInputs or 
                         ImGuiWindowFlags.AlwaysAutoResize or 
                         ImGuiWindowFlags.NoFocusOnAppearing or 
                         ImGuiWindowFlags.NoNav or
                         ImGuiWindowFlags.NoBackground

        val viewport = ImGui.getMainViewport()
        val padding = 20f
        ImGui.setNextWindowPos(viewport.workPosX + padding, viewport.workPosY + viewport.workSizeY - 150f)
        ImGui.setNextWindowBgAlpha(0.35f)

        if (ImGui.begin("Gamepad Overlay", windowFlags)) {
            val axes = JoystickListener.getAxes(GLFW_JOYSTICK_1)
            val buttons = JoystickListener.getButtons(GLFW_JOYSTICK_1)

            if (axes != null && axes.size >= 4) {
                drawStick("Left Stick", axes[0], axes[1])
                ImGui.sameLine()
                drawStick("Right Stick", axes[2], axes[3])
            } else {
                ImGui.textColored(1f, 0f, 0f, 1f, "No Gamepad Detected")
            }

            if (buttons != null) {
                ImGui.text("Buttons:")
                for (i in 0 until Math.min(buttons.size, 14)) {
                    if (buttons[i]) {
                        ImGui.sameLine()
                        ImGui.text("[$i]")
                    }
                }
            }
            ImGui.end()
        }
    }

    private fun drawStick(label: String, x: Float, y: Float) {
        val drawList = ImGui.getWindowDrawList()
        val pos = ImGui.getCursorScreenPos()
        val radius = 40f
        val center = ImVec2(pos.x + radius, pos.y + radius)
        
        drawList.addCircle(center.x, center.y, radius, ImGui.getColorU32(1f, 1f, 1f, 0.5f), 32, 2f)
        drawList.addCircleFilled(center.x + x * (radius - 10f), center.y + y * (radius - 10f), 8f, ImGui.getColorU32(1f, 0f, 0f, 0.8f))
        
        ImGui.dummy(radius * 2f, radius * 2f)
        ImGui.text(label)
    }
}
