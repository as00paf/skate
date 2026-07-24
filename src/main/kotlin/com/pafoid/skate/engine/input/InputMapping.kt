package com.pafoid.skate.engine.input

import kotlinx.serialization.Serializable

@Serializable
class InputMappings(
    var moveUp: InputBinding = InputBinding(gamepadAxis = 1),  // AXIS_LEFT_Y
    var moveDown: InputBinding = InputBinding(gamepadAxis = 1),  // AXIS_LEFT_Y
    var moveLeft: InputBinding = InputBinding(gamepadAxis = 0),  // AXIS_LEFT_X
    var moveRight: InputBinding = InputBinding(gamepadAxis = 0),  // AXIS_LEFT_X
    var sprint: InputBinding = InputBinding(gamepadAxis = 4),  // AXIS_LEFT_TRIGGER
    var crouch: InputBinding = InputBinding(gamepadButton = 4),  // BUTTON_LB
    var jump: InputBinding = InputBinding(gamepadButton = 0),  // BUTTON_A
    var cameraLookX: InputBinding = InputBinding(gamepadAxis = 2),  // AXIS_RIGHT_X
    var cameraLookY: InputBinding = InputBinding(gamepadAxis = 3),
) {

    fun reset() {
        // Movement
        moveUp = InputBinding(gamepadAxis = 1)
        moveDown = InputBinding(gamepadAxis = 1)
        moveLeft = InputBinding(gamepadAxis = 0)
        moveRight = InputBinding(gamepadAxis = 0)

        // Actions
        jump = InputBinding(gamepadButton = 0)
        sprint = InputBinding(gamepadAxis = 4)
        crouch = InputBinding(gamepadButton = 4)
    }
}