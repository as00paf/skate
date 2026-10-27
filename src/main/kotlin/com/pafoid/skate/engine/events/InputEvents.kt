package com.pafoid.skate.engine.events

import org.joml.Vector2f


sealed class InputEvent(eventName: String) : Event(eventName)

data class JumpPressed(val force: Float) : InputEvent("input.jump_pressed")
data object JumpReleased : InputEvent("input.jump_released")
data class MovementInput(val direction: Vector2f, val magnitude: Float) : InputEvent("input.movement")
data class TrickInput(val trick: String, val isPressed: Boolean) : InputEvent("input.trick")
data class CameraLook(val delta: Vector2f) : InputEvent("input.camera_look")
