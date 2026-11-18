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
