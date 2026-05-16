package com.pafoid.skate.engine.input

import org.joml.Vector2f

interface IInputBuffer {
    fun push(timestamp: Float, mousePos: Vector2f, joystickAxes: FloatArray?)
    fun getFlickVelocity(timeWindow: Float): Vector2f
    fun getJoystickFlickVelocity(jid: Int, timeWindow: Float): Vector2f
    fun getRightStickFlickVelocity(jid: Int, timeWindow: Float): Vector2f
}