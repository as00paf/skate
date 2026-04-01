package com.pafoid.skate.engine.settings

import com.pafoid.skate.engine.input.InputMappings
import kotlinx.serialization.Serializable

@Serializable
data class ProjectSettings(
    var info: ProjectInfo = ProjectInfo(),
    var gameplay: GameplaySettings = GameplaySettings(),
    var inputMappings: InputMappings = InputMappings(),
    var physics: PhysicsSettings = PhysicsSettings()
)

@Serializable
data class ProjectInfo(
    var name: String = "New Project",
    var version: String = "1.0.0",
    var startScene: String = "assets/scenes/main.scene"
)

@Serializable
data class GameplaySettings(
    var movementThreshold: Float = 0.15f,
    var sprintThreshold: Float = 0.65f,
    var jumpImpulse: Float = 300.0f,
    var walkSpeed: Float = 2.5f,
    var runSpeed: Float = 7.5f,
    var rotationSpeed: Float = 15.0f,
    var takeOffTime: Float = 0.4f,
    var inputSmoothing: Float = 10.0f
) {
    fun validate() {
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

@Serializable
data class PhysicsSettings(
    var gravity: Float = -9.81f,
    var timeStep: Float = 1f / 60f
)
