package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

@Serializable
data class HardwareSettings(
    val display: DisplaySettings = DisplaySettings(),
    val graphics: GraphicsSettings = GraphicsSettings(),
    val audio: AudioSettings = AudioSettings()
) {
}