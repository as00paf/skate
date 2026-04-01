package com.pafoid.skate.engine.settings

import com.pafoid.skate.engine.input.InputMappings
import kotlinx.serialization.Serializable

/**
 * Legacy ProjectSettings wrapper for backward compatibility.
 * New code should use the ProjectSettings from SettingsData.kt
 */
@Serializable
@Deprecated("Use ProjectSettings from SettingsData.kt instead")
data class LegacyProjectSettings(
    val info: ProjectInfo = ProjectInfo(),
    val gameplay: GameplaySettings = GameplaySettings(),
    val inputMappings: InputMappings = InputMappings(),
    val physics: PhysicsSettings = PhysicsSettings()
)

@Serializable
data class ProjectInfo(
    val name: String = "New Project",
    val version: String = "1.0.0",
    val startScene: String = ""
)

@Serializable
data class PhysicsSettings(
    val gravity: Float = -9.81f,
    val timeStep: Float = 1f / 60f
)
