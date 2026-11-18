package com.pafoid.skate.engine.terrain

import kotlin.math.ceil

/**
 * Macro colour map (REN-28 §6): the average layer colour per [metresPerTexel] (32 m) of a terrain, from the chunks'
 * splat weights and each layer's mean colour. It shades the far field (REN-28 phase D); pure.
 */
class TerrainMacro(val width: Int, val height: Int, val metresPerTexel: Float, val rgb: ByteArray) {
    fun color(tx: Int, tz: Int): FloatArray {
        val o = (tz * width + tx) * 3
        return floatArrayOf((rgb[o].toInt() and 255) / 255f, (rgb[o + 1].toInt() and 255) / 255f, (rgb[o + 2].toInt() and 255) / 255f)
    }

    companion object {
        const val METRES_PER_TEXEL = 32f

        /**
         * Macro map of a streamed terrain from its [overview] and biome rules (no chunk needed): layer weights at each
         * texel centre's height and slope, coloured by [layerColors]. Run on a worker.
         */
        fun bakeFromOverview(generator: TerrainGenerator, overview: TerrainOverview, layerColors: List<FloatArray>, metresPerTexel: Float = METRES_PER_TEXEL): TerrainMacro {
            val layout = generator.layout
            val side = ceil(layout.size / metresPerTexel).toInt().coerceAtLeast(1)
            val rgb = ByteArray(side * side * 3)
            val map = generator.biomeMap
            val biomeWeights = FloatArray(generator.biomes.size)
            val w = FloatArray(TerrainWeights.LAYERS)
            val e = overview.spacing
            for (tz in 0 until side) for (tx in 0 until side) {
                val x = -layout.half + (tx + 0.5f) * metresPerTexel
                val z = -layout.half + (tz + 0.5f) * metresPerTexel
                val h = overview.heightAt(x, z)
                val dx = overview.heightAt(x + e, z) - overview.heightAt(x - e, z)
                val dz = overview.heightAt(x, z + e) - overview.heightAt(x, z - e)
                val ny = 2f * e / kotlin.math.sqrt(dx * dx + 4f * e * e + dz * dz)
                val slope = Math.toDegrees(kotlin.math.acos(ny.coerceIn(-1f, 1f).toDouble())).toFloat()
                if (map != null) {
                    map.weightsAt(x, z, h, biomeWeights)
                    TerrainWeights.layerWeights(generator.biomes, biomeWeights, h, slope, w)
                } else { w.fill(0f); w[0] = 1f }
                val o = (tz * side + tx) * 3
                for (ch in 0 until 3) {
                    var c = 0f
                    for (l in 0 until TerrainWeights.LAYERS) c += w[l] * (layerColors.getOrNull(l)?.get(ch) ?: 0f)
                    rgb[o + ch] = (c.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
                }
            }
            return TerrainMacro(side, side, metresPerTexel, rgb)
        }

        fun bake(layout: TerrainLayout, chunks: List<TerrainChunkData>, layerColors: List<FloatArray>, metresPerTexel: Float = METRES_PER_TEXEL): TerrainMacro {
            val side = ceil(layout.size / metresPerTexel).toInt().coerceAtLeast(1)
            val sum = DoubleArray(side * side * 3)
            val count = IntArray(side * side)
            val w = FloatArray(TerrainWeights.LAYERS)
            for (c in chunks) {
                val splat = c.splat ?: continue
                val n = c.chunkQuads + 1
                for (j in 0 until n) for (i in 0 until n) {
                    val x = c.cornerX + i * c.spacing + layout.half
                    val z = c.cornerZ + j * c.spacing + layout.half
                    val t = ((z / metresPerTexel).toInt().coerceIn(0, side - 1)) * side + (x / metresPerTexel).toInt().coerceIn(0, side - 1)
                    TerrainWeights.unpack(splat, (j * n + i) * TerrainWeights.LAYERS, w)
                    for (l in 0 until TerrainWeights.LAYERS) {
                        val col = layerColors.getOrNull(l) ?: continue
                        sum[t * 3] += w[l] * col[0]; sum[t * 3 + 1] += w[l] * col[1]; sum[t * 3 + 2] += w[l] * col[2]
                    }
                    count[t]++
                }
            }
            val rgb = ByteArray(side * side * 3)
            for (t in 0 until side * side) {
                val k = count[t].coerceAtLeast(1)
                for (ch in 0 until 3) rgb[t * 3 + ch] = ((sum[t * 3 + ch] / k).coerceIn(0.0, 1.0) * 255 + 0.5).toInt().toByte()
            }
            return TerrainMacro(side, side, metresPerTexel, rgb)
        }
    }
}
