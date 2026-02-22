package com.pafoid.skate.editor.data

import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.utils.UnitSystem
import kotlinx.serialization.Serializable

/**
 * Main system settings container for the engine.
 *
 * Contains all configurable settings including:
 * - Display settings (resolution, vsync, fullscreen)
 * - Gameplay settings (unit system, language)
 * - Input mappings (keyboard and gamepad bindings)
 * - Input configuration (deadzones, sensitivities, thresholds)
 *
 * This class is serializable for saving/loading to configuration files.
 */
@Serializable
data class SystemSettings(
    // Display
    var width: Int = 1920,
    var height: Int = 1080,
    var vsync: Boolean = true,
    var fullscreen: Boolean = false,
    var borderless: Boolean = false,

    // UI
    var gamepadOverlaySize: Float = 0.225f,
    var showGamepadOverlay: Boolean = true,

    // Gameplay
    var unitSystem: UnitSystem = UnitSystem.METRIC,
    var language: String = "en",

    // Input Configuration (new structure)
    var inputMappings: InputMappings = InputMappings(),
    var inputSettings: InputSettings = InputSettings(),

    // Legacy key bindings (kept for backwards compatibility, deprecated)
    @Deprecated("Use inputMappings instead", replaceWith = ReplaceWith("inputMappings"))
    var keyBindings: KeyBindings = KeyBindings()
)

/**
 * Input configuration settings for deadzones, sensitivities, and thresholds.
 *
 * All values are configurable to allow players to fine-tune their input experience.
 *
 * ## Usage
 *
 * ```kotlin
 * val settings = InputSettings()
 *
 * // Adjust for more responsive controls
 * settings.leftStickDeadzone = 0.1f  // Lower = more sensitive
 * settings.mouseSensitivity = 0.15f  // Higher = faster look
 *
 * // Adjust for gameplay balance
 * settings.jumpImpulse = 350.0f  // Higher jumps
 * settings.walkSpeed = 3.0f      // Faster walking
 * ```
 */
@Serializable
data class InputSettings(
    // ========================================================================
    // DEADZONE CONFIGURATION
    // ========================================================================

    /**
     * Deadzone threshold for left analog stick (movement).
     * Values below this threshold are ignored to prevent drift.
     * Range: 0.0 - 1.0
     * Default: 0.15f
     */
    var leftStickDeadzone: Float = 0.15f,

    /**
     * Deadzone threshold for right analog stick (camera look).
     * Values below this threshold are ignored to prevent drift.
     * Range: 0.0 - 1.0
     * Default: 0.1f
     */
    var rightStickDeadzone: Float = 0.1f,

    /**
     * Activation threshold for trigger axes.
     * Trigger values below this are considered not pressed.
     * Range: 0.0 - 1.0
     * Default: 0.5f
     */
    var triggerThreshold: Float = 0.5f,

    // ========================================================================
    // SENSITIVITY CONFIGURATION
    // ========================================================================

    /**
     * Mouse sensitivity for camera look.
     * Higher values = faster camera movement.
     * Range: 0.01 - 1.0 (recommended)
     * Default: 0.1f
     */
    var mouseSensitivity: Float = 0.1f,

    /**
     * Gamepad right stick sensitivity for camera look.
     * Higher values = faster camera movement.
     * Range: 0.1 - 10.0 (recommended)
     * Default: 2.0f
     */
    var controllerSensitivity: Float = 2.0f,

    // ========================================================================
    // MOVEMENT THRESHOLDS
    // ========================================================================

    /**
     * Minimum input magnitude required to register movement.
     * Prevents accidental micro-movements from triggering motion.
     * Range: 0.0 - 0.5 (recommended)
     * Default: 0.15f
     */
    var movementThreshold: Float = 0.15f,

    /**
     * Input magnitude threshold for automatic sprint activation.
     * If input exceeds this value, sprint is automatically engaged.
     * Range: 0.5 - 1.0
     * Default: 0.65f
     */
    var sprintThreshold: Float = 0.65f,

    // ========================================================================
    // PHYSICS CONFIGURATION
    // ========================================================================

    /**
     * Jump impulse force applied to the player.
     * Higher values = higher jumps.
     * Units: Newtons (N)
     * Default: 300.0f
     */
    var jumpImpulse: Float = 300.0f,

    /**
     * Default walking speed.
     * Units: meters per second (m/s)
     * Default: 2.5f
     */
    var walkSpeed: Float = 2.5f,

    /**
     * Sprinting/running speed.
     * Units: meters per second (m/s)
     * Default: 7.5f
     */
    var runSpeed: Float = 7.5f,

    /**
     * Character rotation speed when turning.
     * Units: radians per second
     * Default: 10f
     */
    var rotationSpeed: Float = 10f,

    /**
     * Time required to charge jump before release (pop time).
     * Units: seconds
     * Default: 0.9f
     */
    var takeOffTime: Float = 0.9f,

    /**
     * Input smoothing factor for movement.
     * Higher values = faster response, lower values = smoother input.
     * Range: 1.0 - 20.0 (recommended)
     * Default: 12f
     */
    var inputSmoothing: Float = 12f
) {
    /**
     * Validate all settings are within acceptable ranges.
     * Clamps values to valid ranges if they are out of bounds.
     */
    fun validate() {
        // Deadzones
        leftStickDeadzone = leftStickDeadzone.coerceIn(0f, 1f)
        rightStickDeadzone = rightStickDeadzone.coerceIn(0f, 1f)
        triggerThreshold = triggerThreshold.coerceIn(0f, 1f)

        // Sensitivities
        mouseSensitivity = mouseSensitivity.coerceIn(0.01f, 1f)
        controllerSensitivity = controllerSensitivity.coerceIn(0.1f, 10f)

        // Thresholds
        movementThreshold = movementThreshold.coerceIn(0f, 0.5f)
        sprintThreshold = sprintThreshold.coerceIn(0.5f, 1f)

        // Physics
        jumpImpulse = jumpImpulse.coerceIn(100f, 1000f)
        walkSpeed = walkSpeed.coerceIn(1f, 5f)
        runSpeed = runSpeed.coerceIn(5f, 15f)
        rotationSpeed = rotationSpeed.coerceIn(1f, 30f)
        takeOffTime = takeOffTime.coerceIn(0.1f, 2f)
        inputSmoothing = inputSmoothing.coerceIn(1f, 20f)
    }
}

/**
 * Legacy key bindings data class.
 *
 * @deprecated Use [InputMappings] instead for comprehensive input binding support.
 * This class is kept for backwards compatibility with existing save files.
 */
@Serializable
@Deprecated("Use InputMappings instead")
data class KeyBindings(
    var gizmoTranslate: Int = 87, // W
    var gizmoRotate: Int = 69,    // E
    var gizmoScale: Int = 82,     // R
    var gizmoSelect: Int = 81,    // Q
    var gizmoMeasure: Int = 77,   // M
    var deselect: Int = 256       // Escape
)
