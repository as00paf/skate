package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

/**
 * Graphics quality presets.
 */
@Serializable
enum class GraphicsQuality {
    LOW,
    MEDIUM,
    HIGH,
    ULTRA
}