package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.IWindowWithScene
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.input.IInputProvider
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Debug window for testing and visualizing input state.
 *
 * Displays:
 * - Raw gamepad axis values with deadzone visualization
 * - Raw gamepad button states
 * - Processed InputStateComponent values
 * - Current input settings (deadzones, thresholds, sensitivities)
 *
 * @param inputProvider Provider for raw hardware inputs
 * @param stringManager String manager for localization
 */
class InputTestingWindow(
    private val inputProvider: IInputProvider,
    private val stringManager: StringManager,
    private val gameObjectManager: GameObjectManager
) : IWindowWithScene {

    private var showRawAxes = true
    private var showProcessedState = true
    private var showSettings = true
    private var showBindings = false

    /**
     * Renders the input testing debug window.
     *
     * @param currentScene Current scene for accessing InputStateComponent
     */
    override fun imgui(currentScene: Scene) {
        ImGui.begin(stringManager.getString("window.input_testing"))

        // Collapsing headers for sections
        showRawAxes = ImGui.collapsingHeader(stringManager.getString("lbl.input_testing.raw_gamepad"), ImGuiWindowFlags.None)
        if (showRawAxes) {
            renderRawGamepadSection()
        }

        showProcessedState = ImGui.collapsingHeader(stringManager.getString("lbl.input_testing.processed_state"), ImGuiWindowFlags.None)
        if (showProcessedState) {
            renderProcessedStateSection()
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

    /**
     * Renders the raw gamepad input section.
     * Shows axis values, button states, and deadzone visualization.
     */
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

            // Deadzone visualization (TODO Phase 5: Get from settings)
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
                        ImGui.text("${buttonNames[index] ?: "B$index"}")
                    }
                }
            }
        } else {
            MImGui.warningText("  " + stringManager.getString("lbl.input_testing.no_button_data"))
        }

        ImGui.unindent()
    }

    /**
     * Renders a visual deadzone indicator showing current stick position relative to deadzone.
     */
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

    /**
     * Applies deadzone to a value, returning 0 if within deadzone.
     */
    private fun applyDeadzone(value: Float, deadzone: Float): Float {
        return if (abs(value) < deadzone) 0f else value
    }

    /**
     * Renders the processed input state section.
     * Shows values from InputStateComponent.
     */
    private fun renderProcessedStateSection() {
        ImGui.indent()

        val skater = gameObjectManager.getGameObject("Skater")
        val inputState = skater?.getComponent<InputStateComponent>()

        if (inputState == null) {
            MImGui.errorText(stringManager.getString("lbl.input_testing.no_input_state"))
            ImGui.unindent()
            return
        }

        ImGui.separator()

        // Movement
        ImGui.text(stringManager.getString("lbl.input_testing.movement"))
        ImGui.text("  " + stringManager.getString("lbl.input_testing.move_direction").format(inputState.moveDirection.x, inputState.moveDirection.y))
        ImGui.text("  " + stringManager.getString("lbl.input_testing.sprint").format(if (inputState.sprintPressed) "true" else "false"))
        ImGui.text("  " + stringManager.getString("lbl.input_testing.crouch").format(if (inputState.crouchPressed) "true" else "false"))

        ImGui.spacing()

        // Jump
        ImGui.text(stringManager.getString("lbl.input_testing.jump"))
        ImGui.text("  " + stringManager.getString("lbl.input_testing.jump_pressed").format(if (inputState.jumpPressed) "true" else "false"))
        ImGui.text("  " + stringManager.getString("lbl.input_testing.jump_held").format(if (inputState.jumpHeld) "true" else "false"))

        ImGui.spacing()

        // Tricks
        ImGui.text(stringManager.getString("lbl.input_testing.tricks"))
        ImGui.text("  Flip Left: ${inputState.flipLeftPressed}")
        ImGui.text("  Flip Right: ${inputState.flipRightPressed}")
        ImGui.text("  Kickflip: ${inputState.kickflipPressed}")
        ImGui.text("  Heelflip: ${inputState.heelflipPressed}")
        ImGui.text("  Grab: ${inputState.grabPressed}")
        ImGui.text("  Manual: ${inputState.manualPressed}")

        ImGui.spacing()

        // Camera
        ImGui.text(stringManager.getString("lbl.input_testing.camera"))
        ImGui.text("  " + stringManager.getString("lbl.input_testing.camera_look").format(inputState.cameraLook.x, inputState.cameraLook.y))
        ImGui.text("  " + stringManager.getString("lbl.input_testing.camera_reset_val").format(if (inputState.cameraResetPressed) "true" else "false"))

        ImGui.spacing()

        // Game State
        ImGui.text(stringManager.getString("lbl.input_testing.game_state"))
        ImGui.text("  " + stringManager.getString("lbl.input_testing.pause_val").format(if (inputState.pausePressed) "true" else "false"))
        ImGui.text("  " + stringManager.getString("lbl.input_testing.reset_val").format(if (inputState.resetPressed) "true" else "false"))
        ImGui.text("  " + stringManager.getString("lbl.input_testing.stance_val").format(if (inputState.stanceChangePressed) "true" else "false"))

        ImGui.spacing()

        // Physics State
        ImGui.text(stringManager.getString("lbl.input_testing.physics_state"))
        if (inputState.isGrounded) {
            MImGui.successText("  " + stringManager.getString("lbl.input_testing.is_grounded").format(inputState.isGrounded))
        } else {
            MImGui.errorText("  " + stringManager.getString("lbl.input_testing.is_grounded").format(inputState.isGrounded))
        }

        ImGui.unindent()
    }

    /**
     * Renders the input settings section.
     * Shows current deadzone, threshold, and sensitivity values.
     */
    private fun renderSettingsSection() {
        ImGui.indent()
        MImGui.textDisabled("Input settings configuration will be available after Phase 5 completion")
        ImGui.unindent()
    }

    /**
     * Renders the input bindings section.
     * Shows current keyboard and gamepad bindings for all actions.
     *
     * TODO Phase 5: Update to use new immutable settings structure
     */
    private fun renderBindingsSection() {
        ImGui.indent()
        MImGui.textDisabled("Input bindings configuration will be available after Phase 5 completion")
        ImGui.unindent()
    }

    /**
     * Gets human-readable name for a GLFW key code.
     */
    private fun getKeyName(keyCode: Int): String {
        return when (keyCode) {
            GLFW.GLFW_KEY_W -> "W"
            GLFW.GLFW_KEY_A -> "A"
            GLFW.GLFW_KEY_S -> "S"
            GLFW.GLFW_KEY_D -> "D"
            GLFW.GLFW_KEY_SPACE -> "SPACE"
            GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT"
            GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL"
            GLFW.GLFW_KEY_Q -> "Q"
            GLFW.GLFW_KEY_E -> "E"
            GLFW.GLFW_KEY_R -> "R"
            GLFW.GLFW_KEY_ESCAPE -> "ESC"
            GLFW.GLFW_KEY_DELETE -> "DEL"
            GLFW.GLFW_KEY_LEFT -> "LEFT"
            GLFW.GLFW_KEY_RIGHT -> "RIGHT"
            GLFW.GLFW_KEY_LEFT_ALT -> "LALT"
            GLFW.GLFW_KEY_M -> "M"
            -1 -> "N/A"
            else -> "Key$keyCode"
        }
    }

    /**
     * Gets array of gamepad button names for display.
     */
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
