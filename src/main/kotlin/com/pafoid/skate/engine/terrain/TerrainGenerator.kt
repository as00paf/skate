package com.pafoid.skate.engine.terrain

import com.pafoid.skate.engine.procedural.NoiseField
import com.pafoid.skate.engine.procedural.ProceduralSeeds
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Evaluates a [TerrainRecipe] and its stamps at any terrain-local (x, z) (REN-28 §5). Noise layers are seeded from the
 * project seed and `"<seedName>/<step name>"` ([ProceduralSeeds]), never from a sequential stream, so a sample does not
 * depend on generation order, chunk count or thread count. Thread-safe: chunks are built on workers.
 */
class TerrainGenerator(
    recipe: TerrainRecipe,
    val stamps: List<TerrainStamp>,
    val layout: TerrainLayout,
    val heightScale: Float,
    val projectSeed: Long,
    val seedName: String,
    val seedOffset: Int = 0,
    /** Biomes (REN-28 §6); empty: no splat weights or scatter. */
    val biomes: List<Biome> = emptyList(),
    climate: BiomeClimate = BiomeClimate(),
    val biomeSource: BiomeSource = BiomeSource.PROCEDURAL,
) {
    /** Procedural biome weights; null without biomes. */
    val biomeMap: BiomeMap? = if (biomes.isEmpty()) null else BiomeMap(biomes, climate, projectSeed, seedName, seedOffset)

    /** Every biome's scatter rules, flattened (rule index = position here). */
    val scatterEntries: List<ScatterPlacer.Entry> = ScatterPlacer.entries(biomes)

    private sealed class Compiled {
        class Noise(val field: NoiseField, val amplitude: Float, val offset: Float) : Compiled()
        class Warp(val fx: NoiseField, val fz: NoiseField, val strength: Float) : Compiled()
        class Other(val step: TerrainStep) : Compiled()
    }

    private val steps: List<Compiled> = recipe.steps.map { step ->
        when (step) {
            is TerrainStep.Noise -> Compiled.Noise(ProceduralSeeds.field(step.settings, projectSeed, "$seedName/${step.name}", seedOffset), step.amplitude, step.offset)
            is TerrainStep.DomainWarp -> Compiled.Warp(
                ProceduralSeeds.field(step.settings, projectSeed, "$seedName/${step.name}/x", seedOffset),
                ProceduralSeeds.field(step.settings, projectSeed, "$seedName/${step.name}/z", seedOffset),
                step.strength,
            )
            else -> Compiled.Other(step)
        }
    }

    /** Stamp overlap index with one cell per chunk. */
    val stampIndex = StampIndex(stamps, layout.chunkSize)

    /** Recipe height in metres at local ([x], [z]), before stamps. */
    fun recipeHeight(x: Float, z: Float): Float {
        var px = x
        var pz = z
        var h = 0f
        for (c in steps) {
            when (c) {
                is Compiled.Noise -> h += c.amplitude * c.field.sample(px, pz) + c.offset
                is Compiled.Warp -> {
                    val wx = c.fx.sample(px, pz)
                    val wz = c.fz.sample(px, pz)
                    px += c.strength * wx
                    pz += c.strength * wz
                }
                is Compiled.Other -> h = applyShaping(c.step, h, x, z)
            }
        }
        return h * heightScale
    }

    /** Final height in metres at local ([x], [z]): recipe, then every stamp that reaches it. */
    fun heightAt(x: Float, z: Float): Float {
        val h = recipeHeight(x, z)
        if (stampIndex.isEmpty) return h
        return stampIndex.apply(stampIndex.overlapping(x, z, x, z), x, z, h)
    }

    private fun applyShaping(step: TerrainStep, h: Float, x: Float, z: Float): Float = when (step) {
        is TerrainStep.Falloff -> falloff(step, h, x, z)
        is TerrainStep.Terrace -> terrace(h, step.steps, step.sharpness)
        is TerrainStep.Curve -> curve(step.points, h)
        is TerrainStep.Clamp -> h.coerceIn(step.min, max(step.min, step.max))
        else -> h
    }

    private fun falloff(step: TerrainStep.Falloff, h: Float, x: Float, z: Float): Float {
        val half = layout.half
        val d = when (step.shape) {
            FalloffShape.NONE -> return h
            FalloffShape.ISLAND -> sqrt(x * x + z * z) / half
            FalloffShape.SQUARE -> max(abs(x), abs(z)) / half
        }
        val t = smoothstep(step.inner, step.outer, d)
        return h + (step.floor - h) * t
    }

    companion object {
        fun smoothstep(edge0: Float, edge1: Float, v: Float): Float {
            if (edge1 <= edge0) return if (v < edge0) 0f else 1f
            val t = ((v - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** [h] snapped towards [steps] levels per unit; [sharpness] 0 keeps a smooth ramp, 1 a vertical riser. */
        fun terrace(h: Float, steps: Int, sharpness: Float): Float {
            if (steps <= 0) return h
            val k = h * steps
            val f = floor(k)
            val frac = k - f
            val s = sharpness.coerceIn(0f, 1f)
            val w = (1f - s) * 0.5f
            val shaped = if (w <= 0f) (if (frac < 0.5f) 0f else 1f) else smoothstep(0.5f - w, 0.5f + w, frac)
            return (f + shaped) / steps
        }

        /** Piecewise-linear map of [h] through [points] (sorted by x); clamped to the end values. */
        fun curve(points: List<CurvePoint>, h: Float): Float {
            if (points.isEmpty()) return h
            if (h <= points.first().x) return points.first().y
            if (h >= points.last().x) return points.last().y
            for (i in 0 until points.size - 1) {
                val a = points[i]; val b = points[i + 1]
                if (h <= b.x) {
                    val span = b.x - a.x
                    return if (span <= 0f) b.y else a.y + (b.y - a.y) * (h - a.x) / span
                }
            }
            return points.last().y
        }
    }
}
