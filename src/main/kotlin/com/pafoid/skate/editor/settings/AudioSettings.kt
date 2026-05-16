package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

/**
 * Hardware-specific settings for display, graphics, and audio.
 * These settings are auto-detected but can be overridden by the user.
 *
 * @property display Display settings (resolution, fullscreen, VSync)
 * @property graphics Graphics quality settings (quality level, MSAA)
 * @property audio Audio settings (volume, output device)
 */
@Serializable
data class HardwareSettings(
    val display: DisplaySettings = DisplaySettings(),
    val graphics: GraphicsSettings = GraphicsSettings(),
    val audio: AudioSettings = AudioSettings()
) {
    fun validate(): HardwareSettings {
        return copy(
            display = display.validate(),
            graphics = graphics.validate(),
            audio = audio.validate()
        )
    }
}

/**
 * Audio settings for volume and output device.
 */
@Serializable
data class AudioSettings(
    val masterVolume: Float = 1.0f,
    val musicVolume: Float = 0.8f,
    val sfxVolume: Float = 1.0f,
    val outputDevice: String = ""
) {
    fun validate(): AudioSettings {
        return copy(
            masterVolume = masterVolume.coerceIn(0f, 1f),
            musicVolume = musicVolume.coerceIn(0f, 1f),
            sfxVolume = sfxVolume.coerceIn(0f, 1f)
        )
    }
}
