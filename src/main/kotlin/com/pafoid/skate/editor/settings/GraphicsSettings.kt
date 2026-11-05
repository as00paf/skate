package com.pafoid.skate.editor.settings

import com.pafoid.skate.engine.render.shadow.ShadowQualityLevel
import com.pafoid.skate.engine.render.shadow.ShadowSettings
import kotlinx.serialization.Serializable

@Serializable
data class GraphicsSettings(
    val quality: GraphicsQuality = GraphicsQuality.HIGH,
    val msaa: Int = 4,
    val shadowQuality: ShadowQuality = ShadowQuality.MEDIUM,
    val textureQuality: TextureQuality = TextureQuality.HIGH
) {
    /** The engine shadow tier for [shadowQuality]. */
    val shadowQualityLevel: ShadowQualityLevel get() = shadowQuality.toLevel()

    /** Pushes these settings to the engine; the shadow pass picks the new tier up on its next frame. */
    fun applyToEngine() {
        ShadowSettings.quality = shadowQualityLevel
    }
}

/** Editor setting -> engine tier, matched by name so the engine never imports editor types. */
fun ShadowQuality.toLevel(): ShadowQualityLevel = ShadowQualityLevel.fromName(name)

/** Engine tier -> editor setting (inverse of [toLevel]). */
fun ShadowQualityLevel.toSetting(): ShadowQuality = ShadowQuality.valueOf(name)
