package com.pafoid.skate.engine.render.lighting

import com.pafoid.skate.engine.render.lighting.IblMath.RadianceImage
import org.joml.Vector3f

/**
 * The baked image-based lighting of one sky (REN-27), GL-free: a prefiltered radiance mip chain (level `l` is the sky
 * blurred for roughness `l / (LEVELS - 1)`, encoded with [IblMath.encode] in RGBA8) and 9 cosine-convolved SH
 * coefficients for diffuse irradiance. Built by [bake] from the sky's display texels.
 */
class EnvironmentBake(val levels: List<ByteArray>, val shIrradiance: FloatArray) {

    companion object {
        const val BASE_WIDTH = 256
        const val BASE_HEIGHT = 128
        const val LEVELS = 6
        const val SAMPLES = 48
        const val LUT_SIZE = 32
        const val LUT_SAMPLES = 128

        fun width(level: Int) = maxOf(1, BASE_WIDTH shr level)
        fun height(level: Int) = maxOf(1, BASE_HEIGHT shr level)

        /** Perceptual roughness the prefiltered [level] holds; the shader reads level `roughness * (LEVELS - 1)`. */
        fun roughnessOf(level: Int): Float = level.toFloat() / (LEVELS - 1)

        /**
         * Sky radiance from [display] RGBA8 texels ([width] x [height], rows of `v` 0 upward, as the GPU capture reads
         * them back): tinted and exposed like the sky dome, faded to [fogColor] below the horizon like the dome, then
         * [IblMath.decode]d to the radiance a mirror must reflect.
         */
        fun radiance(display: ByteArray, width: Int, height: Int, tint: Vector3f, exposure: Float, fogColor: Vector3f): RadianceImage {
            require(display.size >= width * height * 4) { "${display.size} bytes for ${width}x$height RGBA8" }
            val out = RadianceImage(width, height)
            for (y in 0 until height) {
                val sky = IblMath.horizonBlend((y + 0.5f) / height)
                for (x in 0 until width) {
                    val i = (y * width + x) * 4
                    fun channel(c: Int, t: Float, fog: Float): Float {
                        val d = (display[i + c].toInt() and 0xFF) / 255f * t * exposure
                        return IblMath.decode(fog + (d - fog) * sky)
                    }
                    out.set(x, y, channel(0, tint.x, fogColor.x), channel(1, tint.y, fogColor.y), channel(2, tint.z, fogColor.z))
                }
            }
            return out
        }

        /** Prefilters [radiance] into [LEVELS] levels and projects its irradiance onto SH. */
        fun bake(radiance: RadianceImage): EnvironmentBake {
            val base = if (radiance.width == BASE_WIDTH && radiance.height == BASE_HEIGHT) radiance
                else resample(radiance, BASE_WIDTH, BASE_HEIGHT)
            val pyramid = IblMath.pyramid(base)
            val levels = (0 until LEVELS).map { level ->
                val image = if (level == 0) base else IblMath.prefilter(pyramid, width(level), height(level), roughnessOf(level), SAMPLES)
                encode(image)
            }
            val sh = IblMath.shConvolve(IblMath.shProject(pyramid.getOrElse(2) { pyramid.last() }))
            return EnvironmentBake(levels, sh)
        }

        /** The split-sum BRDF table as RGBA8 (A in red, B in green), [LUT_SIZE] square, rows of roughness upward. */
        val brdfLutRgba8: ByteArray by lazy {
            val lut = IblMath.brdfLut(LUT_SIZE, LUT_SAMPLES)
            ByteArray(LUT_SIZE * LUT_SIZE * 4).also { out ->
                for (i in 0 until LUT_SIZE * LUT_SIZE) {
                    out[i * 4] = toByte(lut[i * 2])
                    out[i * 4 + 1] = toByte(lut[i * 2 + 1])
                    out[i * 4 + 3] = -1
                }
            }
        }

        /** RGBA8 texels of [image], each channel [IblMath.encode]d, alpha opaque. */
        fun encode(image: RadianceImage): ByteArray {
            val out = ByteArray(image.width * image.height * 4)
            for (p in 0 until image.width * image.height) {
                for (c in 0 until 3) out[p * 4 + c] = toByte(IblMath.encode(image.rgb[p * 3 + c]))
                out[p * 4 + 3] = -1
            }
            return out
        }

        private fun toByte(v: Float): Byte = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()

        private fun resample(image: RadianceImage, width: Int, height: Int): RadianceImage {
            val out = RadianceImage(width, height)
            val acc = FloatArray(3)
            for (y in 0 until height) for (x in 0 until width) {
                acc.fill(0f)
                image.sampleAdd((x + 0.5f) / width, (y + 0.5f) / height, 1f, acc)
                out.set(x, y, acc[0], acc[1], acc[2])
            }
            return out
        }
    }
}
