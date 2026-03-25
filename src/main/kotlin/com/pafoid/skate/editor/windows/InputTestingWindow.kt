package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.data.InputSettings
import com.pafoid.skate.editor.imgui.IWindowWithScene
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.scene.getGameObject
import com.pafoid.skate.engine.input.IInputProvider
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags
import org.koin.core.component.KoinComponent
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

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
 * @param settingsManager Settings manager for input configuration
 * @param stringManager String manager for localization
 */
class InputTestingWindow(
    private val inputProvider: IInputProvider,
    private val settingsManager: SettingsManager,
    private val stringManager: StringManager
) : IWindowWithScene, KoinComponent {

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
        showRawAxes = ImGui.collapsingHeader("Raw Gamepad Input", ImGuiWindowFlags.None)
        if (showRawAxes) {
            renderRawGamepadSection()
        }

        showProcessedState = ImGui.collapsingHeader("Processed Input State", ImGuiWindowFlags.None)
        if (showProcessedState) {
            renderProcessedStateSection(currentScene)
        }

        showSettings = ImGui.collapsingHeader("Input Settings", ImGuiWindowFlags.None)
        if (showSettings) {
            renderSettingsSection()
        }

        showBindings = ImGui.collapsingHeader("Input Bindings", ImGuiWindowFlags.None)
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

        // Check if gamepad is connected
        val gamepadConnected = inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)
        if (!gamepadConnected) {
            ImGui.textColored(1f, 0.3f, 0.3f, 1f, "No gamepad detected (GLFW_JOYSTICK_1)")
            ImGui.unindent()
            return
        }

        ImGui.textColored(0.3f, 1f, 0.3f, 1f, "Gamepad Connected")

        val axes = inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1)
        val buttons = inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1)

        ImGui.separator()

        // Left Stick (Axes 0, 1)
        ImGui.text("Left Stick (Movement)")
        if (axes != null && axes.size > 1) {
            val leftStickX = axes[0]
            val leftStickY = axes[1]

            // Show raw values
            ImGui.text("  Raw: X=%.3f, Y=%.3f".format(leftStickX, leftStickY))

            // Deadzone visualization
            val deadzone = settingsManager.engine.hardware.leftStickDeadzone
            renderDeadzoneIndicator("  ", leftStickX, leftStickY, deadzone)

            // After deadzone
            val afterDeadzoneX = applyDeadzone(leftStickX, deadzone)
            val afterDeadzoneY = applyDeadzone(leftStickY, deadzone)
            ImGui.text("  After Deadzone: X=%.3f, Y=%.3f".format(afterDeadzoneX, afterDeadzoneY))
        } else {
            ImGui.textColored(1f, 0.5f, 0f, 1f, "  No axis data")
        }

        ImGui.spacing()

        // Right Stick (Axes 2, 3)
        ImGui.text("Right Stick (Camera)")
        if (axes != null && axes.size > 3) {
            val rightStickX = axes[2]
            val rightStickY = axes[3]

            ImGui.text("  Raw: X=%.3f, Y=%.3f".format(rightStickX, rightStickY))

            val deadzone = settingsManager.engine.hardware.rightStickDeadzone
            renderDeadzoneIndicator("  ", rightStickX, rightStickY, deadzone)

            val afterDeadzoneX = applyDeadzone(rightStickX, deadzone)
            val afterDeadzoneY = applyDeadzone(rightStickY, deadzone)
            ImGui.text("  After Deadzone: X=%.3f, Y=%.3f".format(afterDeadzoneX, afterDeadzoneY))
        } else {
            ImGui.textColored(1f, 0.5f, 0f, 1f, "  No axis data")
        }

        ImGui.spacing()

        // Triggers (Axes 4, 5)
        ImGui.text("Triggers")
        if (axes != null && axes.size > 4) {
            val leftTrigger = axes[4]
            val rightTrigger = if (axes.size > 5) axes[5] else 0f
            ImGui.text("  Left Trigger: %.3f".format(leftTrigger))
            ImGui.text("  Right Trigger: %.3f".format(rightTrigger))
        }

        ImGui.separator()

        // Button states
        ImGui.text("Buttons")
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
            ImGui.textColored(1f, 0.5f, 0f, 1f, "  No button data")
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
        val magnitude = kotlin.math.sqrt(x * x + y * y)
        val status = if (magnitude < deadzone) "WITHIN DEADZONE" else "ACTIVE"
        val statusColor =
            if (magnitude < deadzone) floatArrayOf(0.5f, 0.5f, 0.5f, 1f) else floatArrayOf(0.3f, 1f, 0.3f, 1f)
        ImGui.textColored(statusColor[0], statusColor[1], statusColor[2], statusColor[3], "${prefix}Status: $status")
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
    private fun renderProcessedStateSection(currentScene: Scene) {
        ImGui.indent()

        val skater = currentScene.getGameObject("Skater")
        val inputState = skater?.getComponent<InputStateComponent>()

        if (inputState == null) {
            ImGui.textColored(1f, 0.3f, 0.3f, 1f, "No InputStateComponent found on Skater")
            ImGui.unindent()
            return
        }

        ImGui.separator()

        // Movement
        ImGui.text("Movement")
        ImGui.text("  Move Direction: X=%.3f, Y=%.3f".format(inputState.moveDirection.x, inputState.moveDirection.y))
        ImGui.text("  Sprint: ${inputState.sprintPressed}")
        ImGui.text("  Crouch: ${inputState.crouchPressed}")

        ImGui.spacing()

        // Jump
        ImGui.text("Jump")
        ImGui.text("  Jump Pressed: ${inputState.jumpPressed}")
        ImGui.text("  Jump Held: ${inputState.jumpHeld}")

        ImGui.spacing()

        // Tricks
        ImGui.text("Tricks")
        ImGui.text("  Flip Left: ${inputState.flipLeftPressed}")
        ImGui.text("  Flip Right: ${inputState.flipRightPressed}")
        ImGui.text("  Kickflip: ${inputState.kickflipPressed}")
        ImGui.text("  Heelflip: ${inputState.heelflipPressed}")
        ImGui.text("  Grab: ${inputState.grabPressed}")
        ImGui.text("  Manual: ${inputState.manualPressed}")

        ImGui.spacing()

        // Camera
        ImGui.text("Camera")
        ImGui.text("  Camera Look: X=%.3f, Y=%.3f".format(inputState.cameraLook.x, inputState.cameraLook.y))
        ImGui.text("  Camera Reset: ${inputState.cameraResetPressed}")

        ImGui.spacing()

        // Game State
        ImGui.text("Game State")
        ImGui.text("  Pause: ${inputState.pausePressed}")
        ImGui.text("  Reset: ${inputState.resetPressed}")
        ImGui.text("  Stance Change: ${inputState.stanceChangePressed}")

        ImGui.spacing()

        // Physics State
        ImGui.text("Physics State")
        val groundedColor =
            if (inputState.isGrounded) floatArrayOf(0.3f, 1f, 0.3f, 1f) else floatArrayOf(1f, 0.3f, 0.3f, 1f)
        ImGui.textColored(
            groundedColor[0],
            groundedColor[1],
            groundedColor[2],
            1f,
            "  Is Grounded: ${inputState.isGrounded}"
        )

        ImGui.unindent()
    }

    /**
     * Renders the input settings section.
     * Shows current deadzone, threshold, and sensitivity values.
     */
    private fun renderSettingsSection() {
        ImGui.indent()

        val hardware = settingsManager.engine.hardware
        val gameplay = settingsManager.project.gameplay

        // Hardware Deadzones
        ImGui.text("Hardware Calibration")
        val leftDeadzone = floatArrayOf(hardware.leftStickDeadzone)
        if (ImGui.dragFloat("  Left Stick Deadzone", leftDeadzone, 0.01f, 0f, 0.5f)) {
            hardware.leftStickDeadzone = leftDeadzone[0].coerceIn(0f, 0.5f)
        }

        val rightDeadzone = floatArrayOf(hardware.rightStickDeadzone)
        if (ImGui.dragFloat("  Right Stick Deadzone", rightDeadzone, 0.01f, 0f, 0.5f)) {
            hardware.rightStickDeadzone = rightDeadzone[0].coerceIn(0f, 0.5f)
        }

        val triggerThreshold = floatArrayOf(hardware.triggerThreshold)
        if (ImGui.dragFloat("  Trigger Threshold", triggerThreshold, 0.01f, 0f, 1f)) {
            hardware.triggerThreshold = triggerThreshold[0].coerceIn(0f, 1f)
        }

        ImGui.spacing()

        // Gameplay Constants
        ImGui.text("Gameplay Constants")
        val jumpImpulse = floatArrayOf(gameplay.jumpImpulse)
        if (ImGui.dragFloat("  Jump Impulse", jumpImpulse, 1f, 100f, 1000f)) {
            gameplay.jumpImpulse = jumpImpulse[0].coerceIn(100f, 1000f)
        }

        val walkSpeed = floatArrayOf(gameplay.walkSpeed)
        if (ImGui.dragFloat("  Walk Speed", walkSpeed, 0.1f, 1f, 5f)) {
            gameplay.walkSpeed = walkSpeed[0].coerceIn(1f, 5f)
        }

        val runSpeed = floatArrayOf(gameplay.runSpeed)
        if (ImGui.dragFloat("  Run Speed", runSpeed, 0.1f, 5f, 15f)) {
            gameplay.runSpeed = runSpeed[0].coerceIn(5f, 15f)
        }

        // Reset button
        ImGui.separator()
        if (ImGui.button("Reset to Defaults")) {
            settingsManager.save() // Just a save for now
        }

        ImGui.unindent()
    }

    /**
     * Renders the input bindings section.
     * Shows current keyboard and gamepad bindings for all actions.
     */
    private fun renderBindingsSection() {
        ImGui.indent()

        val mappings = settingsManager.project.inputMappings

        ImGui.text("Movement")
        ImGui.text("  Move Up: Key=${getKeyName(mappings.moveUp.keyboardKey)}, Axis=${mappings.moveUp.gamepadAxis}")
        ImGui.text("  Move Down: Key=${getKeyName(mappings.moveDown.keyboardKey)}, Axis=${mappings.moveDown.gamepadAxis}")
        ImGui.text("  Move Left: Key=${getKeyName(mappings.moveLeft.keyboardKey)}, Axis=${mappings.moveLeft.gamepadAxis}")
        ImGui.text("  Move Right: Key=${getKeyName(mappings.moveRight.keyboardKey)}, Axis=${mappings.moveRight.gamepadAxis}")
        ImGui.text("  Sprint: Key=${getKeyName(mappings.sprint.keyboardKey)}, Axis=${mappings.sprint.gamepadAxis}")
        ImGui.text("  Crouch: Key=${getKeyName(mappings.crouch.keyboardKey)}, Button=${mappings.crouch.gamepadButton}")

        ImGui.spacing()

        ImGui.text("Jump")
        ImGui.text("  Jump: Key=${getKeyName(mappings.jump.keyboardKey)}, Button=${mappings.jump.gamepadButton}")

        ImGui.spacing()

        ImGui.text("Tricks")
        ImGui.text("  Flip Left: Key=${getKeyName(mappings.flipLeft.keyboardKey)}, Button=${mappings.flipLeft.gamepadButton}")
        ImGui.text("  Flip Right: Key=${getKeyName(mappings.flipRight.keyboardKey)}, Button=${mappings.flipRight.gamepadButton}")
        ImGui.text("  Kickflip: Key=${getKeyName(mappings.kickflip.keyboardKey)}, Button=${mappings.kickflip.gamepadButton}")
        ImGui.text("  Heelflip: Key=${getKeyName(mappings.heelflip.keyboardKey)}, Button=${mappings.heelflip.gamepadButton}")
        ImGui.text("  Grab: Key=${getKeyName(mappings.grab.keyboardKey)}, Button=${mappings.grab.gamepadButton}")
        ImGui.text("  Manual: Key=${getKeyName(mappings.manual.keyboardKey)}, Button=${mappings.manual.gamepadButton}")

        ImGui.spacing()

        ImGui.text("Camera")
        ImGui.text("  Camera Look X: Axis=${mappings.cameraLookX.gamepadAxis}")
        ImGui.text("  Camera Look Y: Axis=${mappings.cameraLookY.gamepadAxis}")
        ImGui.text("  Camera Reset: Key=${getKeyName(mappings.cameraReset.keyboardKey)}, Button=${mappings.cameraReset.gamepadButton}")

        ImGui.spacing()

        ImGui.text("Game State")
        ImGui.text("  Pause: Key=${getKeyName(mappings.pause.keyboardKey)}, Button=${mappings.pause.gamepadButton}")
        ImGui.text("  Reset: Key=${getKeyName(mappings.reset.keyboardKey)}, Button=${mappings.reset.gamepadButton}")
        ImGui.text("  Stance Change: Key=${getKeyName(mappings.stanceChange.keyboardKey)}, Button=${mappings.stanceChange.gamepadButton}")
        ImGui.text("  Stance Change Right: Key=${getKeyName(mappings.stanceChangeRight.keyboardKey)}, Button=${mappings.stanceChangeRight.gamepadButton}")

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
