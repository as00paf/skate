package com.pafoid.skate.engine.utils

import kotlinx.serialization.Serializable

@Serializable
data class SystemSettings(
    var width: Int = 1920,
    var height: Int = 1080,
    var vsync: Boolean = true,
    var fullscreen: Boolean = false,
    var borderless: Boolean = false,
    var gamepadOverlaySize: Float = 0.225f,
    var showGamepadOverlay: Boolean = true,
    var unitSystem: UnitSystem = UnitSystem.METRIC,
    var language: String = "en",
    var keyBindings: KeyBindings = KeyBindings()
)

@Serializable
data class KeyBindings(
    var gizmoTranslate: Int = 87, // W
    var gizmoRotate: Int = 69,    // E
    var gizmoScale: Int = 82,     // R
    var gizmoSelect: Int = 81,    // Q
    var gizmoMeasure: Int = 77,   // M
    var deselect: Int = 256       // Escape
)