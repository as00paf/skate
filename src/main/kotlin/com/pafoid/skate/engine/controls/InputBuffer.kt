package com.pafoid.skate.engine.controls

import org.joml.Vector2f
import java.util.*

data class InputState(
    val timestamp: Float,
    val mousePos: Vector2f,
    val joystickAxes: FloatArray?
)

object InputBuffer {
    private const val MAX_SAMPLES = 60
    private val buffer: Deque<InputState> = ArrayDeque()

    fun push(timestamp: Float, mousePos: Vector2f, joystickAxes: FloatArray?) {
        if (buffer.size >= MAX_SAMPLES) {
            buffer.removeFirst()
        }
        buffer.addLast(InputState(timestamp, Vector2f(mousePos), joystickAxes?.copyOf()))
    }

    fun getFlickVelocity(timeWindow: Float): Vector2f {
        if (buffer.size < 2) return Vector2f(0f, 0f)

        val now = buffer.last.timestamp
        val startState = buffer.find { (now - it.timestamp) <= timeWindow } ?: buffer.first
        val endState = buffer.last

        val deltaPos = Vector2f(endState.mousePos).sub(startState.mousePos)
        val deltaTime = endState.timestamp - startState.timestamp

        return if (deltaTime > 0) deltaPos.div(deltaTime) else Vector2f(0f, 0f)
    }
    
    fun getJoystickFlickVelocity(jid: Int, timeWindow: Float): Vector2f {
        return getJoystickFlickVelocity(jid, timeWindow, JoystickListener.AXIS_LEFT_X, JoystickListener.AXIS_LEFT_Y)
    }

    fun getRightStickFlickVelocity(jid: Int, timeWindow: Float): Vector2f {
        return getJoystickFlickVelocity(jid, timeWindow, JoystickListener.AXIS_RIGHT_X, JoystickListener.AXIS_RIGHT_Y)
    }

    private fun getJoystickFlickVelocity(jid: Int, timeWindow: Float, axisX: Int, axisY: Int): Vector2f {
        if (buffer.size < 2) return Vector2f(0f, 0f)

        val now = buffer.last.timestamp
        val startState = buffer.find { (now - it.timestamp) <= timeWindow } ?: buffer.first
        val endState = buffer.last
        
        val startAxes = startState.joystickAxes ?: return Vector2f(0f, 0f)
        val endAxes = endState.joystickAxes ?: return Vector2f(0f, 0f)
        
        val maxAxis = Math.max(axisX, axisY)
        if (startAxes.size <= maxAxis || endAxes.size <= maxAxis) return Vector2f(0f, 0f)

        val deltaPos = Vector2f(endAxes[axisX] - startAxes[axisX], endAxes[axisY] - startAxes[axisY])
        val deltaTime = endState.timestamp - startState.timestamp

        return if (deltaTime > 0) deltaPos.div(deltaTime) else Vector2f(0f, 0f)
    }
}