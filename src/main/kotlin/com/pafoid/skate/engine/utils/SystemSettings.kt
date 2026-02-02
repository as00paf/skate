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
    var language: String = "en"
)