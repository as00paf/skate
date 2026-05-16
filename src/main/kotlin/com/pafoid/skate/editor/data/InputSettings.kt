package com.pafoid.skate.editor.data

import kotlinx.serialization.Serializable

@Serializable
data class InputSettings(
    var leftStickDeadzone: Float = 0.15f,
    var rightStickDeadzone: Float = 0.1f,
    var triggerThreshold: Float = 0.5f,
    var mouseSensitivity: Float = 0.1f,
    var controllerSensitivity: Float = 2.0f,
    var movementThreshold: Float = 0.15f,
    var sprintThreshold: Float = 0.65f,
    var jumpImpulse: Float = 300.0f,
    var walkSpeed: Float = 2.5f,
    var runSpeed: Float = 7.5f,
    var rotationSpeed: Float = 10f,
    var takeOffTime: Float = 0.9f,
    var inputSmoothing: Float = 12f
) {
    fun validate() {
        leftStickDeadzone = leftStickDeadzone.coerceIn(0f, 1f)
        rightStickDeadzone = rightStickDeadzone.coerceIn(0f, 1f)
        triggerThreshold = triggerThreshold.coerceIn(0f, 1f)
        mouseSensitivity = mouseSensitivity.coerceIn(0.01f, 1f)
        controllerSensitivity = controllerSensitivity.coerceIn(0.1f, 10f)
        movementThreshold = movementThreshold.coerceIn(0f, 0.5f)
        sprintThreshold = sprintThreshold.coerceIn(0.5f, 1f)
        jumpImpulse = jumpImpulse.coerceIn(100f, 1000f)
        walkSpeed = walkSpeed.coerceIn(1f, 5f)
        runSpeed = runSpeed.coerceIn(5f, 15f)
        rotationSpeed = rotationSpeed.coerceIn(1f, 30f)
        takeOffTime = takeOffTime.coerceIn(0.1f, 2f)
        inputSmoothing = inputSmoothing.coerceIn(1f, 20f)
    }
}
