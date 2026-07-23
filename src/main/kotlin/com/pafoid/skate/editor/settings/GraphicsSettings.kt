package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

@Serializable
data class GraphicsSettings(
    val quality: GraphicsQuality = GraphicsQuality.HIGH,
    val msaa: Int = 4,
    val shadowQuality: ShadowQuality = ShadowQuality.MEDIUM,
    val textureQuality: TextureQuality = TextureQuality.HIGH
) {
}