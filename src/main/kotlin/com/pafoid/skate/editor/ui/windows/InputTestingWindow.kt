package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.sqrt

class InputTestingWindow(
    engine: Engine,
) : EditorWindow("window.input_testing") {
    private val stringManager = engine.stringManager
    private val inputProvider = engine.inputProvider

    private var showRawAxes = true
    private var showSettings = true
    private var showBindings = false

    override fun imgui() {
        ImGui.begin(stringManager.getString("window.input_testing"))

        // Collapsing headers for sections
        showRawAxes = ImGui.collapsingHeader(stringManager.getString("lbl.input_testing.raw_gamepad"), ImGuiWindowFlags.None)
        if (showRawAxes) {
            renderRawGamepadSection()
        }

        showSettings = ImGui.collapsingHeader(stringManager.getString("lbl.input_testing.settings"), ImGuiWindowFlags.None)
        if (showSettings) {
            renderSettingsSection()
        }

        showBindings = ImGui.collapsingHeader(stringManager.getString("lbl.input_testing.bindings"), ImGuiWindowFlags.None)
        if (showBindings) {
            renderBindingsSection()
        }

        ImGui.end()
    }

    private fun renderRawGamepadSection() {
        ImGui.indent()

        val gamepadConnected = inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)
        if (!gamepadConnected) {
            MImGui.errorText(stringManager.getString("lbl.input_testing.no_gamepad"))
            ImGui.unindent()
            return
        }

        MImGui.successText(stringManager.getString("lbl.input_testing.gamepad_connected"))

        val axes = inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1)
        val buttons = inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1)

        ImGui.separator()

        // Left Stick (Axes 0, 1)
        ImGui.text(stringManager.getString("lbl.input_testing.left_stick"))
        if (axes != null && axes.size > 1) {
            val leftStickX = axes[0]
            val leftStickY = axes[1]

            // Show raw values
            ImGui.text("  " + stringManager.getString("lbl.input_testing.raw_values").format(leftStickX, leftStickY))

            // Deadzone visualization
            val deadzone = 0.15f // settingsManager.engine.hardware.leftStickDeadzone
            renderDeadzoneIndicator("  ", leftStickX, leftStickY, deadzone)

            // After deadzone
            val afterDeadzoneX = applyDeadzone(leftStickX, deadzone)
            val afterDeadzoneY = applyDeadzone(leftStickY, deadzone)
            ImGui.text("  " + stringManager.getString("lbl.input_testing.after_deadzone").format(afterDeadzoneX, afterDeadzoneY))
        } else {
            MImGui.warningText("  " + stringManager.getString("lbl.input_testing.no_axis_data"))
        }

        ImGui.spacing()

        // Right Stick (Axes 2, 3)
        ImGui.text(stringManager.getString("lbl.input_testing.right_stick"))
        if (axes != null && axes.size > 3) {
            val rightStickX = axes[2]
            val rightStickY = axes[3]

            ImGui.text("  " + stringManager.getString("lbl.input_testing.raw_values").format(rightStickX, rightStickY))

            val deadzone = 0.1f // settingsManager.engine.hardware.rightStickDeadzone
            renderDeadzoneIndicator("  ", rightStickX, rightStickY, deadzone)

            val afterDeadzoneX = applyDeadzone(rightStickX, deadzone)
            val afterDeadzoneY = applyDeadzone(rightStickY, deadzone)
            ImGui.text("  " + stringManager.getString("lbl.input_testing.after_deadzone").format(afterDeadzoneX, afterDeadzoneY))
        } else {
            MImGui.warningText("  " + stringManager.getString("lbl.input_testing.no_axis_data"))
        }

        ImGui.spacing()

        // Triggers (Axes 4, 5)
        ImGui.text(stringManager.getString("lbl.input_testing.triggers"))
        if (axes != null && axes.size > 4) {
            val leftTrigger = axes[4]
            val rightTrigger = if (axes.size > 5) axes[5] else 0f
            ImGui.text("  " + stringManager.getString("lbl.input_testing.left_trigger_val").format(leftTrigger))
            ImGui.text("  " + stringManager.getString("lbl.input_testing.right_trigger_val").format(rightTrigger))
        }

        ImGui.separator()

        // Button states
        ImGui.text(stringManager.getString("lbl.input_testing.buttons"))
        if (buttons != null) {
            val buttonNames = getGamepadButtonNames()
            val columns = 4
            val rows = (buttons.size + columns - 1) / columns

            for (row in 0 until rows) {
                ImGui.sameLine(0f)
                for (col in 0 until columns) {
                    val index = row + col * rows
                    if (index < buttons.size) {
                        val isPressed = buttons[index]
                        val color =
                            if (isPressed) floatArrayOf(0.3f, 1f, 0.3f, 1f) else floatArrayOf(0.5f, 0.5f, 0.5f, 1f)
                        ImGui.colorButton("##Btn$index", color[0], color[1], color[2], color[3], 20f, 20f)
                        ImGui.sameLine()
                        ImGui.text(buttonNames[index] ?: "B$index")
                    }
                }
            }
        } else {
            MImGui.warningText("  " + stringManager.getString("lbl.input_testing.no_button_data"))
        }

        ImGui.unindent()
    }

    private fun renderDeadzoneIndicator(prefix: String, x: Float, y: Float, deadzone: Float) {
        val size = 100f
        val center = size / 2

        // Draw deadzone circle background
        ImGui.sameLine()
        val drawList = ImGui.getWindowDrawList()
        val pos = ImGui.getCursorScreenPos()

        // Background
        drawList.addCircleFilled(
            pos.x + center, pos.y + center,
            center,
            ImGui.getColorU32(ImGuiCol.FrameBg)
        )

        // Deadzone circle
        val deadzoneRadius = (deadzone * center).coerceAtLeast(5f)
        drawList.addCircle(
            pos.x + center, pos.y + center,
            deadzoneRadius,
            ImGui.getColorU32(0.5f, 0.5f, 0.5f, 1f),
            16
        )

        // Stick position (inverted Y for display)
        val stickX = center + x * center
        val stickY = center - y * center

        // Stick indicator
        drawList.addCircleFilled(
            pos.x + stickX, pos.y + stickY,
            5f,
            ImGui.getColorU32(1f, 0.5f, 0f, 1f)
        )

        // Border
        drawList.addCircle(
            pos.x + center, pos.y + center,
            center,
            ImGui.getColorU32(ImGuiCol.Border),
            16,
            2f
        )

        ImGui.dummy(size, size)

        // Show if within deadzone
        val magnitude = sqrt(x * x + y * y)
        val status = if (magnitude < deadzone) stringManager.getString("lbl.input_testing.within_deadzone") else stringManager.getString("lbl.input_testing.active")
        if (magnitude < deadzone) {
            MImGui.textDisabled("${prefix}Status: $status")
        } else {
            MImGui.successText("${prefix}Status: $status")
        }
    }

    private fun applyDeadzone(value: Float, deadzone: Float): Float {
        return if (abs(value) < deadzone) 0f else value
    }

    private fun renderSettingsSection() {
        ImGui.indent()
        MImGui.textDisabled("Input settings configuration will be available after Phase 5 completion")
        ImGui.unindent()
    }

    private fun renderBindingsSection() {
        ImGui.indent()
        MImGui.textDisabled("Input bindings configuration will be available after Phase 5 completion")
        ImGui.unindent()
    }

    private fun getGamepadButtonNames(): Array<String?> {
        return arrayOf(
            "A",      // 0
            "B",      // 1
            "X",      // 2
            "Y",      // 3
            "LB",     // 4
            "RB",     // 5
            "BACK",   // 6
            "START",  // 7
            "LS",     // 8
            "RS",     // 9
            "HOME",   // 10
            null,     // 11 (reserved)
            null,     // 12 (reserved)
            "DPAD-L", // 13
            "DPAD-R", // 14
            "DPAD-U", // 15
            "DPAD-D"  // 16
        )
    }
}
