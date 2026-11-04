package com.pafoid.skate.engine.render.shadow

/**
 * Engine-side shadow quality tiers. Names match the editor's `ShadowQuality` setting so callers can map with
 * [fromName] without the engine importing editor code.
 *
 * @param cascadeCount Directional-light cascades (1..[CascadeSplits.MAX_CASCADES]).
 * @param cascadeResolution Size of each cascade layer in texels.
 * @param pcfRadius PCF kernel radius in texels: 0 = one hardware 2x2 compare, 1 = 3x3, 2 = 5x5.
 * @param localResolution Size of each spot/point shadow layer in texels.
 * @param localLayers Layers for spot (1 each) and point (6 each) shadows; 0 disables local shadows.
 */
enum class ShadowQualityLevel(
    val cascadeCount: Int,
    val cascadeResolution: Int,
    val pcfRadius: Int,
    val localResolution: Int,
    val localLayers: Int,
) {
    LOW(cascadeCount = 1, cascadeResolution = 1024, pcfRadius = 0, localResolution = 512, localLayers = 6),
    MEDIUM(cascadeCount = 3, cascadeResolution = 2048, pcfRadius = 1, localResolution = 1024, localLayers = 12),
    HIGH(cascadeCount = 4, cascadeResolution = 2048, pcfRadius = 2, localResolution = 1024, localLayers = LocalShadows.MAX_LAYERS);

    companion object {
        val DEFAULT = MEDIUM

        /** Maps a setting name (e.g. `GraphicsSettings.shadowQuality.name`) to a tier; unknown names give [DEFAULT]. */
        fun fromName(name: String?): ShadowQualityLevel = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/** Process-wide shadow quality. The editor/game settings write it; [com.pafoid.skate.engine.render.renderer.passes.ShadowPass] reads it every frame. */
object ShadowSettings {
    /** JVM property that picks the starting tier, e.g. `-Dskate.shadowQuality=HIGH`. */
    const val QUALITY_PROPERTY = "skate.shadowQuality"

    @Volatile
    var quality: ShadowQualityLevel = ShadowQualityLevel.fromName(System.getProperty(QUALITY_PROPERTY))

    /** Steps LOW -> MEDIUM -> HIGH -> LOW and returns the new tier. */
    fun cycle(): ShadowQualityLevel {
        val all = ShadowQualityLevel.entries
        quality = all[(quality.ordinal + 1) % all.size]
        return quality
    }
}
