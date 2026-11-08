package com.pafoid.skate.engine.render.renderer.passes

/**
 * Engine-side post-processing quality tier (separate from the editor's [GraphicsQuality] to avoid engine→editor dependency).
 *
 * Maps to post-processing effect configurations via [PostProcessQualityPreset].
 */
enum class PostProcessQuality {
    LOW,
    MEDIUM,
    HIGH,
    ULTRA;

    companion object {
        val DEFAULT = MEDIUM

        /** Maps an editor [GraphicsQuality] name to this tier; unknown names give [DEFAULT]. */
        fun fromGraphicsQuality(name: String): PostProcessQuality = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
