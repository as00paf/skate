package com.pafoid.skate.engine.terrain

import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.procedural.NoiseSettings
import kotlinx.serialization.Serializable

/**
 * A biome (REN-28 §6): a name, a debug colour, material layer rules (which of the terrain's layers cover the ground,
 * by height and slope) and scatter rules (what grows or lies on it, §7).
 */
@Serializable
data class Biome(
    val name: String = "Biome",
    /** RGB 0..1, for the biome debug view. */
    val debugColor: List<Float> = listOf(1f, 0f, 1f),
    val layerRules: List<LayerRule> = listOf(LayerRule()),
    val scatter: List<ScatterRule> = emptyList(),
)

/**
 * Weight of terrain layer [layer] by height (m, terrain-local) and slope (degrees): full inside
 * [minHeight]..[maxHeight] × [minSlope]..[maxSlope], fading to 0 over [blendHeight] metres and [blendSlope] degrees
 * outside, times [weight]. Several rules of one biome add up; weights are normalized per sample.
 */
@Serializable
data class LayerRule(
    val layer: Int = 0,
    val weight: Float = 1f,
    val minHeight: Float = -1e6f,
    val maxHeight: Float = 1e6f,
    val minSlope: Float = 0f,
    val maxSlope: Float = 90f,
    val blendHeight: Float = 2f,
    val blendSlope: Float = 5f,
) {
    fun weightAt(height: Float, slopeDeg: Float): Float {
        val h = band(height, minHeight, maxHeight, blendHeight)
        if (h <= 0f) return 0f
        return weight * h * band(slopeDeg, minSlope, maxSlope, blendSlope)
    }

    private fun band(v: Float, lo: Float, hi: Float, blend: Float): Float = when {
        v in lo..hi -> 1f
        blend <= 0f -> 0f
        v < lo -> (1f - (lo - v) / blend).coerceAtLeast(0f)
        else -> (1f - (v - hi) / blend).coerceAtLeast(0f)
    }
}

/** Where a terrain's biome ids come from (REN-28 §6). */
@Serializable
enum class BiomeSource { PROCEDURAL, PAINTED }

/**
 * Procedural biome map settings (REN-28 §6): two low-frequency climate fields, *temperature* and *moisture* in
 * [-1, 1] (seeded `"<seedName>/climate/t"` and `".../m"`), temperature dropping by [lapseRate] per metre of height,
 * then a [gridSize] × [gridSize] Whittaker-style lookup ([grid]: biome indices, row = moisture, column = temperature,
 * cold/dry first). Borders blend the 4 nearest grid cells by inverse distance, sharpened by [borderSharpness].
 */
@Serializable
data class BiomeClimate(
    val temperature: NoiseSettings = NoiseSettings(frequency = 0.004f, octaves = 2),
    val moisture: NoiseSettings = NoiseSettings(frequency = 0.003f, octaves = 2),
    val lapseRate: Float = 0.02f,
    val gridSize: Int = 3,
    val grid: List<Int> = listOf(
        2, 0, 1,
        2, 0, 1,
        2, 0, 0,
    ),
    val borderSharpness: Float = 4f,
)

/** Default biomes over the default layers (0 sand, 1 grass, 2 rock, 3 snow), in grid order: grassland, beach, peak. */
object BiomePresets {
    const val SAND = 0
    const val GRASS = 1
    const val ROCK = 2
    const val SNOW = 3

    /** Placeholder rock model until the bundled CC0 rocks (AST): a squashed engine sphere. */
    const val ROCK_MODEL = "engine:Models/sphere.obj"

    /** Grey rock material of the default scatter rules (REN-43): without it the sphere renders plain white. */
    const val ROCK_MATERIAL = EngineDefaults.Materials.ROCK

    /**
     * A scatter rule of breakable crates for the stateful props check (REN-28 task 27): a prefab with behaviour,
     * spawned as entities near the player; broken or taken crates stay that way (§15.10), optionally coming back after
     * [respawnAfterSeconds].
     */
    fun breakableCrates(prefab: String, respawnAfterSeconds: Float = 0f) = ScatterRule(
        id = "crates", prefab = prefab, densityPer100m2 = 0.3f, minSpacing = 6f, maxSlope = 20f,
        scaleMin = 1f, scaleMax = 1f, yawJitter = 360f, collider = true, maxEntities = 64, respawnAfterSeconds = respawnAfterSeconds,
    )

    fun defaults(): MutableList<Biome> = mutableListOf(
        Biome(
            name = "Grassland", debugColor = listOf(0.3f, 0.8f, 0.2f),
            layerRules = listOf(
                LayerRule(GRASS, maxSlope = 30f, blendSlope = 8f),
                LayerRule(ROCK, minSlope = 30f, blendSlope = 8f),
                LayerRule(SAND, maxHeight = 0.5f, blendHeight = 1f, weight = 2f),
            ),
            scatter = listOf(ScatterRule(id = "rocks", model = ROCK_MODEL, material = ROCK_MATERIAL, densityPer100m2 = 0.4f, minSpacing = 5f,
                scaleMin = 0.3f, scaleMax = 0.9f, maxSlope = 30f, collider = true, alignToNormal = 0.6f)),
        ),
        Biome(
            name = "Beach", debugColor = listOf(0.95f, 0.85f, 0.4f),
            layerRules = listOf(LayerRule(SAND, maxSlope = 35f, blendSlope = 8f), LayerRule(ROCK, minSlope = 35f, blendSlope = 8f)),
        ),
        Biome(
            name = "Rocky peak", debugColor = listOf(0.55f, 0.55f, 0.6f),
            layerRules = listOf(LayerRule(ROCK), LayerRule(SNOW, minHeight = 40f, blendHeight = 6f, maxSlope = 40f, blendSlope = 8f, weight = 2f)),
            scatter = listOf(ScatterRule(id = "boulders", model = ROCK_MODEL, material = ROCK_MATERIAL, densityPer100m2 = 0.2f, minSpacing = 8f,
                scaleMin = 0.8f, scaleMax = 2f, maxSlope = 45f, collider = true, alignToNormal = 0.8f)),
        ),
    )
}
