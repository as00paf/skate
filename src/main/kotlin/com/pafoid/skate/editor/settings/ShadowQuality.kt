package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

/**
 * Shadow quality settings.
 */
@Serializable
enum class ShadowQuality {
    LOW,
    MEDIUM,
    HIGH
}