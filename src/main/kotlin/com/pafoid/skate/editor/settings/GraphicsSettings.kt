package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

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