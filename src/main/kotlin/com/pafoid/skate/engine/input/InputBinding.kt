package com.pafoid.skate.engine.input

import kotlinx.serialization.Serializable
import org.lwjgl.glfw.GLFW

@Serializable
data class InputBinding(
    var keyboardKey: Int = -1,
    var gamepadButton: Int = -1,
    var gamepadAxis: Int = -1,
    var inverted: Boolean = false
) {
    // TODO: check if still needed
    fun isValid(): Boolean = keyboardKey >= 0 || gamepadButton >= 0 || gamepadAxis >= 0
    fun isKeyboardOnly(): Boolean = keyboardKey >= 0 && gamepadButton < 0 && gamepadAxis < 0
    fun isGamepadOnly(): Boolean = keyboardKey < 0 && (gamepadButton >= 0 || gamepadAxis >= 0)
    fun isAxisBinding(): Boolean = gamepadAxis >= 0
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
}