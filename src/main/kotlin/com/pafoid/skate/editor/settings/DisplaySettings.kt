package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

/**
 * Display settings for resolution and window mode.
 */
@Serializable
data class DisplaySettings(
    val width: Int = 1920,
    val height: Int = 1080,
    val fullscreen: Boolean = false,
    val vsync: Boolean = true
) {
    /**
     * Get resolution as "Width x Height" string.
     */
    fun getResolutionString(): String = "${width}x${height}"

    /**
     * Validate resolution is within reasonable bounds.
     */
    fun validate(): DisplaySettings {
        return copy(
            width = width.coerceIn(800, 3840),
            height = height.coerceIn(600, 2160)
        )
    }
}