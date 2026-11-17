package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.ecs.components.TerrainLayer
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.material.MaterialSchema
import com.pafoid.skate.engine.terrain.TerrainWeights

/** A decoded RGBA8 image (rows top first, 4 bytes per pixel). */
class RgbaImage(val width: Int, val height: Int, val pixels: ByteArray) {
    init { require(pixels.size == width * height * 4) }
}

/** Bilinear RGBA8 resampling, pure (layer textures of any size go into one fixed-size array). */
object ImageResample {
    fun bilinear(src: RgbaImage, width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height * 4)
        for (y in 0 until height) {
            val sy = ((y + 0.5f) * src.height / height - 0.5f).coerceIn(0f, (src.height - 1).toFloat())
            val y0 = sy.toInt(); val y1 = minOf(y0 + 1, src.height - 1); val fy = sy - y0
            for (x in 0 until width) {
                val sx = ((x + 0.5f) * src.width / width - 0.5f).coerceIn(0f, (src.width - 1).toFloat())
                val x0 = sx.toInt(); val x1 = minOf(x0 + 1, src.width - 1); val fx = sx - x0
                for (c in 0 until 4) {
                    fun p(px: Int, py: Int) = (src.pixels[(py * src.width + px) * 4 + c].toInt() and 255).toFloat()
                    val top = p(x0, y0) + (p(x1, y0) - p(x0, y0)) * fx
                    val bottom = p(x0, y1) + (p(x1, y1) - p(x0, y1)) * fx
                    out[(y * width + x) * 4 + c] = (top + (bottom - top) * fy + 0.5f).toInt().coerceIn(0, 255).toByte()
                }
            }
        }
        return out
    }
}

/**
 * Builds the three layer images of one terrain layer (REN-28 §4.2) at [size]²: albedo (RGB × base colour, A = height
 * from luminance: stones poke through sand), normal (tangent space) and ORM (occlusion, roughness, metallic, glTF
 * channels). Missing materials or textures fall back to [DEFAULT_COLORS] and flat maps, so a terrain always shades.
 */
object TerrainLayerImages {
    /** Sand, grass, rock, snow, then neutral greys (RGB 0..1). */
    val DEFAULT_COLORS = listOf(
        floatArrayOf(0.76f, 0.70f, 0.50f), floatArrayOf(0.30f, 0.45f, 0.18f), floatArrayOf(0.42f, 0.40f, 0.38f),
        floatArrayOf(0.92f, 0.94f, 0.97f), floatArrayOf(0.5f, 0.45f, 0.4f), floatArrayOf(0.35f, 0.3f, 0.25f),
        floatArrayOf(0.6f, 0.6f, 0.6f), floatArrayOf(0.25f, 0.25f, 0.25f),
    )
    private val DEFAULT_ROUGHNESS = floatArrayOf(0.95f, 0.9f, 0.85f, 0.6f, 0.9f, 0.9f, 0.8f, 0.8f)

    class Layer(val albedo: ByteArray, val normal: ByteArray, val orm: ByteArray, val averageColor: FloatArray)

    fun build(
        index: Int,
        layer: TerrainLayer?,
        size: Int,
        material: (String) -> MaterialAsset?,
        decode: (String) -> RgbaImage?,
    ): Layer {
        val asset = layer?.material?.takeIf { it.isNotBlank() }?.let(material)
        val base = asset?.params?.get(MaterialSchema.Companion.Param.BASE_COLOR)?.let { floatArrayOf(it[0], it[1], it[2]) }
            ?: if (asset != null) floatArrayOf(1f, 1f, 1f) else DEFAULT_COLORS[index % DEFAULT_COLORS.size]
        val roughness = asset?.params?.get(MaterialSchema.Companion.Param.ROUGHNESS_FACTOR)?.get(0) ?: DEFAULT_ROUGHNESS[index % 8]
        val metallic = asset?.params?.get(MaterialSchema.Companion.Param.METALLIC_FACTOR)?.get(0) ?: 0f
        fun texture(slot: String) = asset?.textures?.get(slot)?.let(decode)?.let { ImageResample.bilinear(it, size, size) }

        val n = size * size
        val albedo = texture(MaterialSchema.Companion.Tex.BASE_COLOR) ?: ByteArray(n * 4) { 255.toByte() }
        var r = 0.0; var g = 0.0; var b = 0.0
        for (p in 0 until n) {
            val o = p * 4
            val cr = (albedo[o].toInt() and 255) / 255f * base[0]
            val cg = (albedo[o + 1].toInt() and 255) / 255f * base[1]
            val cb = (albedo[o + 2].toInt() and 255) / 255f * base[2]
            albedo[o] = (cr * 255f + 0.5f).toInt().toByte(); albedo[o + 1] = (cg * 255f + 0.5f).toInt().toByte(); albedo[o + 2] = (cb * 255f + 0.5f).toInt().toByte()
            albedo[o + 3] = ((0.3f * cr + 0.59f * cg + 0.11f * cb).coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            r += cr; g += cg; b += cb
        }
        val normal = texture(MaterialSchema.Companion.Tex.NORMAL) ?: ByteArray(n * 4) { k -> FLAT_NORMAL[k % 4] }
        val mr = texture(MaterialSchema.Companion.Tex.METALLIC_ROUGHNESS)
        val ao = texture(MaterialSchema.Companion.Tex.AO)
        val orm = ByteArray(n * 4)
        for (p in 0 until n) {
            val o = p * 4
            val occ = ao?.let { it[o].toInt() and 255 } ?: 255
            val rough = ((mr?.let { (it[o + 1].toInt() and 255) / 255f } ?: 1f) * roughness * 255f).toInt().coerceIn(0, 255)
            val metal = ((mr?.let { (it[o + 2].toInt() and 255) / 255f } ?: 1f) * metallic * 255f).toInt().coerceIn(0, 255)
            orm[o] = occ.toByte(); orm[o + 1] = rough.toByte(); orm[o + 2] = metal.toByte(); orm[o + 3] = 255.toByte()
        }
        return Layer(albedo, normal, orm, floatArrayOf((r / n).toFloat(), (g / n).toFloat(), (b / n).toFloat()))
    }

    /** All [TerrainWeights.LAYERS] layers (missing ones from the defaults). */
    fun buildAll(layers: List<TerrainLayer>, size: Int, material: (String) -> MaterialAsset?, decode: (String) -> RgbaImage?): List<Layer> =
        List(TerrainWeights.LAYERS) { build(it, layers.getOrNull(it), size, material, decode) }

    private val FLAT_NORMAL = byteArrayOf(128.toByte(), 128.toByte(), 255.toByte(), 255.toByte())
}
