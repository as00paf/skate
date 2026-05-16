package com.pafoid.skate.engine.input

import kotlinx.serialization.Serializable
import org.lwjgl.glfw.GLFW

/**
 * Represents a single input binding that can map to keyboard, gamepad button, or gamepad axis.
 *
 * An input binding can have multiple sources (e.g., both keyboard key AND gamepad button).
 * When any source is activated, the binding is considered active.
 *
 * @property keyboardKey GLFW key code for keyboard binding. Default: -1 (no keyboard binding)
 * @property gamepadButton Gamepad button index for gamepad binding. Default: -1 (no gamepad button binding)
 * @property gamepadAxis Gamepad axis index for analog input. Default: -1 (no gamepad axis binding)
 * @property inverted If true, invert the axis value (for axes only). Default: false
 */
@Serializable
data class InputBinding(
    var keyboardKey: Int = -1,
    var gamepadButton: Int = -1,
    var gamepadAxis: Int = -1,
    var inverted: Boolean = false
) {
    /**
     * Check if this binding has any valid input source configured.
     * @return true if at least one source (keyboard, button, or axis) is configured
     */
    fun isValid(): Boolean = keyboardKey >= 0 || gamepadButton >= 0 || gamepadAxis >= 0

    /**
     * Check if this binding is a keyboard-only binding.
     * @return true if only keyboard key is configured
     */
    fun isKeyboardOnly(): Boolean = keyboardKey >= 0 && gamepadButton < 0 && gamepadAxis < 0

    /**
     * Check if this binding is a gamepad-only binding.
     * @return true if only gamepad button or axis is configured
     */
    fun isGamepadOnly(): Boolean = keyboardKey < 0 && (gamepadButton >= 0 || gamepadAxis >= 0)

    /**
     * Check if this binding is for an analog axis input.
     * @return true if gamepad axis is configured
     */
    fun isAxisBinding(): Boolean = gamepadAxis >= 0

    /**
     * Get a human-readable description of this binding.
     * @return String describing the bound input(s)
     */
    fun getDescription(): String {
        val descriptions = mutableListOf<String>()

        if (keyboardKey >= 0) {
            descriptions.add(GLFW.glfwGetKeyName(keyboardKey, 0) ?: "Key $keyboardKey")
        }

        if (gamepadButton >= 0) {
            descriptions.add("Gamepad Button $gamepadButton")
        }

        if (gamepadAxis >= 0) {
            val axisName = when (gamepadAxis) {
                0 -> "Left Stick X"
                1 -> "Left Stick Y"
                2 -> "Right Stick X"
                3 -> "Right Stick Y"
                4 -> "Left Trigger"
                5 -> "Right Trigger"
                else -> "Axis $gamepadAxis"
            }
            val invertStr = if (inverted) " (Inverted)" else ""
            descriptions.add("$axisName$invertStr")
        }

        return if (descriptions.isEmpty()) {
            "Not Bound"
        } else {
            descriptions.joinToString(" / ")
        }
    }

    companion object {
        /**
         * Create a keyboard-only binding.
         * @param key GLFW key code
         * @return InputBinding bound to keyboard key only
         */
        fun keyboard(key: Int): InputBinding = InputBinding(keyboardKey = key)

        /**
         * Create a gamepad button-only binding.
         * @param button Gamepad button index
         * @return InputBinding bound to gamepad button only
         */
        fun gamepadButton(button: Int): InputBinding = InputBinding(gamepadButton = button)

        /**
         * Create a gamepad axis-only binding.
         * @param axis Gamepad axis index
         * @param inverted If true, invert the axis value
         * @return InputBinding bound to gamepad axis
         */
        fun gamepadAxis(axis: Int, inverted: Boolean = false): InputBinding =
            InputBinding(gamepadAxis = axis, inverted = inverted)
    }
}