package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

/**
 * Texture quality settings.
 */
@Serializable
enum class TextureQuality {
    LOW,
    MEDIUM,
    HIGH
}