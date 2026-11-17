package com.pafoid.skate.engine.terrain

import com.pafoid.skate.engine.procedural.NoiseField
import com.pafoid.skate.engine.procedural.ProceduralSeeds
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Procedural biome weights (REN-28 §6), pure and thread-safe: climate (temperature, moisture) at a terrain-local
 * position and height, looked up in the [BiomeClimate] grid, with soft borders from the 4 nearest grid cells.
 */
class BiomeMap(
    val biomes: List<Biome>,
    val climate: BiomeClimate,
    projectSeed: Long,
    seedName: String,
    seedOffset: Int = 0,
) {
    private val temperature: NoiseField = ProceduralSeeds.field(climate.temperature, projectSeed, "$seedName/climate/t", seedOffset)
    private val moisture: NoiseField = ProceduralSeeds.field(climate.moisture, projectSeed, "$seedName/climate/m", seedOffset)
    private val n = climate.gridSize.coerceAtLeast(1)

    /** Climate (temperature, moisture) in [-1, 1] at local ([x], [z]) and [height] metres. */
    fun climateAt(x: Float, z: Float, height: Float, out: FloatArray = FloatArray(2)): FloatArray {
        out[0] = (temperature.sample(x, z) - climate.lapseRate * height).coerceIn(-1f, 1f)
        out[1] = moisture.sample(x, z).coerceIn(-1f, 1f)
        return out
    }

    /** Biome index of grid cell ([tx], [my]), clamped; invalid entries fall back to biome 0. */
    fun cellBiome(tx: Int, my: Int): Int {
        val i = my.coerceIn(0, n - 1) * n + tx.coerceIn(0, n - 1)
        return climate.grid.getOrNull(i)?.takeIf { it in biomes.indices } ?: 0
    }

    /** Normalized biome weights for climate ([t], [m]) into [out] (size = biome count). */
    fun weightsForClimate(t: Float, m: Float, out: FloatArray): FloatArray {
        out.fill(0f)
        if (biomes.isEmpty()) return out
        // Cell centres sit at integer grid coordinates
        val u = (t + 1f) * 0.5f * n - 0.5f
        val v = (m + 1f) * 0.5f * n - 0.5f
        val u0 = floor(u).toInt(); val v0 = floor(v).toInt()
        var total = 0f
        for (dv in 0..1) for (du in 0..1) {
            val cu = u0 + du; val cv = v0 + dv
            val dx = u - cu.coerceIn(0, n - 1); val dy = v - cv.coerceIn(0, n - 1)
            val d = sqrt(dx * dx + dy * dy)
            val w = 1f / (d.pow(climate.borderSharpness) + EPSILON)
            out[cellBiome(cu, cv)] += w
            total += w
        }
        for (i in out.indices) out[i] /= total
        return out
    }

    /** Normalized biome weights at local ([x], [z]) and [height] metres. */
    fun weightsAt(x: Float, z: Float, height: Float, out: FloatArray = FloatArray(biomes.size)): FloatArray {
        val c = climateAt(x, z, height)
        return weightsForClimate(c[0], c[1], out)
    }

    companion object {
        private const val EPSILON = 1e-6f
    }
}

/** Material layer weights and their packing into splat maps (REN-28 §4.2, §6). */
object TerrainWeights {
    const val LAYERS = 8

    /**
     * Layer weights at a sample: Σ biome weight × the biome's layer rules at ([height], [slopeDeg]), normalized; layer 0
     * when no rule matches. [out] has [LAYERS] entries.
     */
    fun layerWeights(biomes: List<Biome>, biomeWeights: FloatArray, height: Float, slopeDeg: Float, out: FloatArray): FloatArray {
        out.fill(0f)
        for (b in biomes.indices) {
            val wb = biomeWeights[b]
            if (wb <= 0f) continue
            for (rule in biomes[b].layerRules) {
                if (rule.layer !in 0 until LAYERS) continue
                out[rule.layer] += wb * rule.weightAt(height, slopeDeg)
            }
        }
        normalize(out)
        return out
    }

    fun normalize(weights: FloatArray) {
        val total = weights.sum()
        if (total <= 0f) { weights.fill(0f); weights[0] = 1f; return }
        for (i in weights.indices) weights[i] /= total
    }

    /** Packs [LAYERS] normalized weights as bytes summing to 255 (largest remainder), into [out] at [offset]. */
    fun pack(weights: FloatArray, out: ByteArray, offset: Int) {
        var used = 0
        val rem = FloatArray(LAYERS)
        for (i in 0 until LAYERS) {
            val v = weights[i] * 255f
            val q = v.toInt()
            out[offset + i] = q.toByte(); used += q; rem[i] = v - q
        }
        var left = 255 - used
        while (left > 0) {
            var best = 0
            for (i in 1 until LAYERS) if (rem[i] > rem[best]) best = i
            out[offset + best] = ((out[offset + best].toInt() and 255) + 1).toByte()
            rem[best] = -1f
            left--
        }
    }

    fun unpack(data: ByteArray, offset: Int, out: FloatArray): FloatArray {
        for (i in 0 until LAYERS) out[i] = (data[offset + i].toInt() and 255) / 255f
        return out
    }

    /**
     * The 4 strongest layers and their renormalized weights (the shader's per-pixel reduction, mirrored for tests):
     * fills [layers] and [weights] (size 4), strongest first; ties keep the lower layer.
     */
    fun topFour(all: FloatArray, layers: IntArray, weights: FloatArray) {
        val taken = BooleanArray(all.size)
        var total = 0f
        for (k in 0 until 4) {
            var best = -1
            for (i in all.indices) if (!taken[i] && (best < 0 || all[i] > all[best])) best = i
            taken[best] = true
            layers[k] = best; weights[k] = all[best]; total += all[best]
        }
        if (total > 0f) for (k in 0 until 4) weights[k] /= total
    }
}
