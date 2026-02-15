package com.pafoid.skate.engine.input

import org.joml.Vector3f

interface IInputProvider {
    fun isKeyPressed(key: Int): Boolean
    fun keyBeginPress(key: Int): Boolean
    fun isJoystickPresent(jid: Int): Boolean
    fun getAxes(jid: Int): FloatArray?
    fun getMovementVector(jid: Int): Vector3f
    fun getButtons(jid: Int): BooleanArray?
    fun buttonPressed(jid: Int, button: Int): Boolean
    fun buttonWasPressed(jid: Int, button: Int): Boolean
    fun buttonBeginPress(jid: Int, button: Int): Boolean
    fun isCursorDisabled(): Boolean
}