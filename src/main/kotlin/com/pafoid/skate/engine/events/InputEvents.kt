package com.pafoid.skate.engine.events

import org.joml.Vector2f

/**
 * Base class for all input events.
 *
 * Input events are published by [com.pafoid.skate.engine.ecs.systems.InputSystem]
 * when the player provides input via gamepad or keyboard.
 */
sealed class InputEvent(eventName: String) : GameEvent(eventName)

/**
 * Published when the jump button is initially pressed.
 *
 * @property force Jump input force (0.0-1.0, from trigger buttons or analog input)
 */
data class JumpPressed(val force: Float) : InputEvent("input.jump_pressed")

/**
 * Published when the jump button is released.
 */
data object JumpReleased : InputEvent("input.jump_released")

/**
 * Published when movement input changes.
 *
 * @property direction Normalized 2D movement direction (-1 to 1 on each axis)
 * @property magnitude Input magnitude (0.0-1.0, after deadzone processing)
 */
data class MovementInput(
    val direction: Vector2f,
    val magnitude: Float
) : InputEvent("input.movement")

/**
 * Types of trick inputs.
 */
enum class TrickType {
    FLIP_LEFT,      // LB / Q key
    FLIP_RIGHT,     // RB / E key
    KICKFLIP,       // X / W key
    HEELFLIP,       // Y / S key
    GRAB,           // A / Space (in air)
    MANUAL          // Back button / Left Alt
}

/**
 * Published when a trick input button is pressed or released.
 *
 * @property trickType Type of trick input
 * @property isPressed True when pressed, false when released
 */
data class TrickInput(
    val trickType: TrickType,
    val isPressed: Boolean
) : InputEvent("input.trick")

/**
 * Published when camera look input changes.
 *
 * @property delta Camera look delta (X = yaw, Y = pitch)
 */
data class CameraLook(val delta: Vector2f) : InputEvent("input.camera_look")
