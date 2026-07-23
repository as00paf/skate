package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

@Serializable
data class DisplaySettings(
    val width: Int = 1920,
    val height: Int = 1080,
    val fullscreen: Boolean = false,
    val vsync: Boolean = true
) {
}