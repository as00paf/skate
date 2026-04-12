package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

/**
 * Auto-save configuration settings.
 */
@Serializable
data class AutoSaveSettings(
    val enabled: Boolean = true,
    val intervalMinutes: Int = 5
) {
    fun validate(): AutoSaveSettings {
        return copy(
            intervalMinutes = intervalMinutes.coerceIn(1, 60)
        )
    }
}