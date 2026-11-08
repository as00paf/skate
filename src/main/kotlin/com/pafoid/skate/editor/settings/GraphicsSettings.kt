package com.pafoid.skate.editor.settings

import com.pafoid.skate.engine.render.MsaaSettings
import com.pafoid.skate.engine.render.renderer.passes.PostProcessQuality
import com.pafoid.skate.engine.render.renderer.passes.PostProcessQualityPreset
import com.pafoid.skate.engine.render.shadow.ShadowQualityLevel
import com.pafoid.skate.engine.render.shadow.ShadowSettings
import com.pafoid.skate.engine.render.texture.TextureQualityLevel
import com.pafoid.skate.engine.render.texture.TextureQualitySettings
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

    /** The engine post-processing tier for [quality]. */
    val postProcessQuality: PostProcessQuality get() = PostProcessQuality.fromGraphicsQuality(quality.name)

    /** The engine texture quality tier for [textureQuality]. */
    val textureQualityLevel: TextureQualityLevel get() = textureQuality.toLevel()

    /** Pushes these settings to the engine; the renderer picks the new tiers up on its next frame. */
    fun applyToEngine() {
        ShadowSettings.quality = shadowQualityLevel
        MsaaSettings.samples = msaa.coerceIn(0, 8)
        TextureQualitySettings.apply(textureQualityLevel, emptyList())
    }
}

/** Editor setting -> engine tier, matched by name so the engine never imports editor types. */
fun ShadowQuality.toLevel(): ShadowQualityLevel = ShadowQualityLevel.fromName(name)

/** Engine tier -> editor setting (inverse of [toLevel]). */
fun ShadowQualityLevel.toSetting(): ShadowQuality = ShadowQuality.valueOf(name)
