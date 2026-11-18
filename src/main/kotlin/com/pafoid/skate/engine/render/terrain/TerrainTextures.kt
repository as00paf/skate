package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import com.pafoid.skate.engine.terrain.TerrainChunkData
import com.pafoid.skate.engine.terrain.TerrainWeights
import org.lwjgl.system.MemoryUtil
import kotlin.math.log2

/**
 * The 8 material layers as three RGBA8 texture arrays (albedo + height, normal, ORM), mipmapped and repeating
 * (REN-28 §4.2). [averageColors] are the layers' mean albedo (macro colour bake, far field).
 */
class TerrainLayerArrays(private val device: RhiDevice, layers: List<TerrainLayerImages.Layer>, val size: Int) : AutoCloseable {
    private val mips = log2(size.toFloat()).toInt() + 1
    private fun array() = device.createTexture(TextureDesc(
        TextureType.TEXTURE_2D_ARRAY, PixelFormat.RGBA8, size, size, layers = TerrainWeights.LAYERS, mipLevels = mips,
        sampler = SamplerDesc(Filter.LINEAR_MIPMAP_LINEAR, Filter.LINEAR, Wrap.REPEAT, anisotropy = 8f),
    ))

    val albedo: RhiTexture = array()
    val normal: RhiTexture = array()
    val orm: RhiTexture = array()
    val averageColors: List<FloatArray> = layers.map { it.averageColor }

    init {
        val buffer = MemoryUtil.memAlloc(size * size * 4)
        try {
            for ((i, layer) in layers.withIndex()) {
                for ((texture, pixels) in listOf(albedo to layer.albedo, normal to layer.normal, orm to layer.orm)) {
                    buffer.clear(); buffer.put(pixels); buffer.flip()
                    texture.upload(0, i, buffer)
                }
            }
        } finally {
            MemoryUtil.memFree(buffer)
        }
        albedo.generateMips(); normal.generateMips(); orm.generateMips()
    }

    override fun close() { albedo.close(); normal.close(); orm.close() }

    companion object {
        /** Layer resolution (REN-28 §4.2 suggests 1024²; 512² keeps the 3 × 8 arrays near 32 MB with mips). */
        const val DEFAULT_SIZE = 512
    }
}

/**
 * Per-chunk splat weights as two RGBA8 texture arrays at `(chunkQuads + 1)²`, one layer per resident chunk
 * (REN-28 §4.2): array 0 holds layers 0–3, array 1 layers 4–7. Capacity is capped by the device's array layers; a
 * chunk without an atlas layer shades by height and slope.
 */
class SplatAtlas(private val device: RhiDevice, val chunkQuads: Int, capacity: Int) : AutoCloseable {
    val samples = chunkQuads + 1
    val capacity: Int = capacity.coerceIn(1, device.caps.maxArrayLayers.coerceAtLeast(1))
    private fun array() = device.createTexture(TextureDesc(
        TextureType.TEXTURE_2D_ARRAY, PixelFormat.RGBA8, samples, samples, layers = this.capacity,
        sampler = SamplerDesc(Filter.LINEAR, Filter.LINEAR, Wrap.CLAMP_TO_EDGE),
    ))
    val first: RhiTexture = array()
    val second: RhiTexture = array()
    private val free = ArrayDeque((0 until this.capacity).toList())
    private val staging = MemoryUtil.memAlloc(samples * samples * 4)

    val freeLayers: Int get() = free.size

    /** A free layer, or -1 when the atlas is full. */
    fun acquire(): Int = free.removeFirstOrNull() ?: -1

    fun release(layer: Int) { if (layer >= 0) free.addLast(layer) }

    /** Uploads [chunk]'s splat (which must be computed) into [layer]. */
    fun upload(layer: Int, chunk: TerrainChunkData) {
        val splat = chunk.splat ?: return
        for ((half, texture) in listOf(0 to first, 1 to second)) {
            staging.clear()
            for (p in 0 until samples * samples) for (c in 0 until 4) staging.put(splat[p * TerrainWeights.LAYERS + half * 4 + c])
            staging.flip()
            texture.upload(0, layer, staging)
        }
    }

    override fun close() { first.close(); second.close(); MemoryUtil.memFree(staging) }
}
