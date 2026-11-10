package com.pafoid.skate.editor.settings

import com.pafoid.skate.engine.render.texture.TextureQualityLevel
import kotlinx.serialization.Serializable

@Serializable
enum class TextureQuality {
    LOW,
    MEDIUM,
    HIGH;

    /** Editor setting -> engine tier, matched by name so the engine never imports editor types. */
    fun toLevel(): TextureQualityLevel = TextureQualityLevel.fromName(name)
}

/** Engine tier -> editor setting (inverse of [toLevel]). */
fun TextureQualityLevel.toSetting(): TextureQuality = TextureQuality.valueOf(name)