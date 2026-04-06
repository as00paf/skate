package com.pafoid.skate.engine.settings

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

/**
 * Graphics quality settings.
 */
@Serializable
data class GraphicsSettings(
    val quality: GraphicsQuality = GraphicsQuality.HIGH,
    val msaa: Int = 4,
    val shadowQuality: ShadowQuality = ShadowQuality.MEDIUM,
    val textureQuality: TextureQuality = TextureQuality.HIGH
) {
    fun validate(): GraphicsSettings {
        return copy(
            msaa = when (msaa) {
                0, 2, 4, 8 -> msaa
                else -> 4
            }.coerceIn(0, 8)
        )
    }
}

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

/**
 * Shadow quality settings.
 */
@Serializable
enum class ShadowQuality {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Texture quality settings.
 */
@Serializable
enum class TextureQuality {
    LOW,
    MEDIUM,
    HIGH
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
