package com.pafoid.skate.engine.input

import org.joml.Vector2f

data class InputState(
    val timestamp: Float,
    val mousePos: Vector2f,
    val joystickAxes: FloatArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InputState

        if (timestamp != other.timestamp) return false
        if (mousePos != other.mousePos) return false
        if (!joystickAxes.contentEquals(other.joystickAxes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + mousePos.hashCode()
        result = 31 * result + (joystickAxes?.contentHashCode() ?: 0)
        return result
    }
}