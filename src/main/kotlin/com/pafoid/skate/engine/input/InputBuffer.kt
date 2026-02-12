package com.pafoid.skate.engine.input

import com.pafoid.skate.engine.input.listeners.GamepadConstants.AXIS_LEFT_X
import com.pafoid.skate.engine.input.listeners.GamepadConstants.AXIS_LEFT_Y
import com.pafoid.skate.engine.input.listeners.GamepadConstants.AXIS_RIGHT_X
import com.pafoid.skate.engine.input.listeners.GamepadConstants.AXIS_RIGHT_Y
import org.joml.Vector2f
import java.util.*
import kotlin.math.max

private const val MAX_SAMPLES = 60

class InputBuffer : IInputBuffer {

    private val buffer: Deque<InputState> = ArrayDeque()

    override fun push(timestamp: Float, mousePos: Vector2f, joystickAxes: FloatArray?) {
        if (buffer.size >= MAX_SAMPLES) {
            buffer.removeFirst()
        }
        buffer.addLast(InputState(timestamp, Vector2f(mousePos), joystickAxes?.copyOf()))
    }

    override fun getFlickVelocity(timeWindow: Float): Vector2f {
        if (buffer.size < 2) return Vector2f(0f, 0f)

        val now = buffer.last.timestamp
        val startState = buffer.find { (now - it.timestamp) <= timeWindow } ?: buffer.first
        val endState = buffer.last

        val deltaPos = Vector2f(endState.mousePos).sub(startState.mousePos)
        val deltaTime = endState.timestamp - startState.timestamp

        return if (deltaTime > 0) deltaPos.div(deltaTime) else Vector2f(0f, 0f)
    }

    private fun getJoystickFlickVelocity(jid: Int, timeWindow: Float, axisX: Int, axisY: Int): Vector2f {
        if (buffer.size < 2) return Vector2f(0f, 0f)

        val now = buffer.last.timestamp
        val startState = buffer.find { (now - it.timestamp) <= timeWindow } ?: buffer.first
        val endState = buffer.last
        
        val startAxes = startState.joystickAxes ?: return Vector2f(0f, 0f)
        val endAxes = endState.joystickAxes ?: return Vector2f(0f, 0f)
        
        val maxAxis = max(axisX, axisY)
        if (startAxes.size <= maxAxis || endAxes.size <= maxAxis) return Vector2f(0f, 0f)

        val deltaPos = Vector2f(endAxes[axisX] - startAxes[axisX], endAxes[axisY] - startAxes[axisY])
        val deltaTime = endState.timestamp - startState.timestamp

        return if (deltaTime > 0) deltaPos.div(deltaTime) else Vector2f(0f, 0f)
    }

    override fun getJoystickFlickVelocity(jid: Int, timeWindow: Float): Vector2f {
        return getJoystickFlickVelocity(jid, timeWindow, AXIS_LEFT_X, AXIS_LEFT_Y)
    }

    override fun getRightStickFlickVelocity(jid: Int, timeWindow: Float): Vector2f {
        return getJoystickFlickVelocity(jid, timeWindow, AXIS_RIGHT_X, AXIS_RIGHT_Y)
    }
}
