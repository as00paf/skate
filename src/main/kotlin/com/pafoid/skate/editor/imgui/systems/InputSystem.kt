package com.pafoid.skate.editor.imgui.systems

import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.systems.InputSystem
import imgui.ImGui
import org.lwjgl.glfw.GLFW

fun InputSystem.imgui(stringManager: StringManager) {
    ImGui.separator()
    ImGui.textColored(
        0.5f,
        0.5f,
        0.5f,
        1f,
        stringManager.getString("lbl.input_system.configuration_pending")
    )
    ImGui.separator()

    ImGui.text(stringManager.getString("lbl.input_system.debug"))

    // Show current gamepad state if connected
    if (inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)) {
        val axes = inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1)
        val buttons = inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1)

        ImGui.text(
            stringManager.getString(
                "lbl.input_system.gamepad_connected",
                stringManager.getString("lbl.input_system.yes")
            )
        )

        if (axes != null) {
            ImGui.text(stringManager.getString("lbl.input_system.left_stick", axes[0], axes[1]))
            ImGui.text(stringManager.getString("lbl.input_system.right_stick", axes[2], axes[3]))
            ImGui.text(stringManager.getString("lbl.input_system.left_trigger", axes.getOrNull(4) ?: 0f))
            ImGui.text(stringManager.getString("lbl.input_system.right_trigger", axes.getOrNull(5) ?: 0f))
        }

        if (buttons != null) {
            ImGui.text(
                stringManager.getString(
                    "lbl.input_system.buttons",
                    buttons.getOrNull(0) ?: false,
                    buttons.getOrNull(1) ?: false,
                    buttons.getOrNull(2) ?: false,
                    buttons.getOrNull(3) ?: false
                )
            )
        }
    } else {
        ImGui.text(
            stringManager.getString(
                "lbl.input_system.gamepad_connected",
                stringManager.getString("lbl.input_system.no")
            )
        )
    }
}