package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.RhiVertexLayout
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import com.pafoid.skate.engine.terrain.TerrainChunkData
import com.pafoid.skate.engine.terrain.TerrainLayout
import com.pafoid.skate.engine.terrain.TerrainMacro
import com.pafoid.skate.engine.terrain.TerrainOverview
import com.pafoid.skate.engine.terrain.streaming.FarFieldQuadtree
import com.pafoid.skate.engine.terrain.streaming.FarTile
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.FrustumIntersection
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil

/**
 * Far field of one streamed terrain (REN-28 §15.4): quadtree tiles ([FarFieldQuadtree]) built from the
 * [TerrainOverview] into pooled tile slots (same grid and stream layout as chunks, [TILE_QUADS] quads), shaded with the
 * macro colour map. Tiles are cached by (level, x, z) and rebuilt only when the overview changes; the pool's capacity
 * bounds them (least recently used first).
 */
class FarFieldRenderer(private val device: RhiDevice, layout: RhiVertexLayout) : AutoCloseable {
    private val mesh = TerrainMeshBuilder(TILE_QUADS)
    private val pool = ChunkBufferPool(mesh, device, layout)
    private val tiles = object : LinkedHashMap<FarTile, ChunkBufferPool.Slot>(64, 0.75f, true) {}
    private val selected = ArrayList<FarTile>()
    private val staging = FloatArray(mesh.vertexCount * TerrainMeshBuilder.STREAM_FLOATS)
    private var overview: TerrainOverview? = null
    private var macro: RhiTexture? = null
    private var macroSource: TerrainMacro? = null
    private val corner = Vector3f()

    val tileCount: Int get() = tiles.size
    var lastDrawn = 0
        private set

    /**
     * Draws the far tiles of a terrain at [position] around [camera] (world) with [shader] (bound: terrain colour or
     * picking program); [detailed] tells which chunks the detail pass draws.
     */
    fun draw(
        shader: Shader, layout: TerrainLayout, overview: TerrainOverview, macro: TerrainMacro?, position: Vector3f,
        camera: Vector3f, farDistance: Float, frustum: FrustumIntersection, detailed: (Int, Int) -> Boolean, colour: Boolean,
        pipeline: () -> com.pafoid.skate.engine.render.rhi.RhiPipeline?,
    ) {
        if (overview !== this.overview) { tiles.values.forEach(pool::release); tiles.clear(); this.overview = overview }
        if (colour && macro != null && macro !== macroSource) uploadMacro(macro)
        FarFieldQuadtree.select(layout, camera.x - position.x, camera.z - position.z, farDistance, detailed, selected)
        val p = pipeline() ?: return
        val cmd = device.commandList()
        cmd.setPipeline(p)
        if (colour) {
            cmd.setTexture(TerrainRenderer.UNIT_SPLAT_0, macro?.let { this.macro })
            shader.uploadInt(Uniforms.TERRAIN_MACRO, TerrainRenderer.UNIT_SPLAT_0)
            shader.uploadInt(Uniforms.TERRAIN_FAR_TILE, if (this.macro != null) 1 else 2)
            shader.uploadVec3f(Uniforms.TERRAIN_MACRO_AREA, corner.set(position.x - layout.half, position.z - layout.half, layout.size))
            shader.uploadInt(Uniforms.TERRAIN_SPLAT_LAYER, -1)
        }
        lastDrawn = 0
        for (tile in selected) {
            val side = (1 shl tile.level) * layout.chunkSize
            val minX = position.x + layout.chunkCornerX(tile.tx shl tile.level)
            val minZ = position.z + layout.chunkCornerZ(tile.tz shl tile.level)
            if (!frustum.testAab(minX, position.y - 1000f, minZ, minX + side, position.y + 2000f, minZ + side)) continue
            val slot = slot(tile, layout, overview) ?: continue
            shader.uploadVec3f(Uniforms.TERRAIN_CORNER, corner.set(minX, position.y, minZ))
            shader.uploadFloat(Uniforms.TERRAIN_SPACING, side / TILE_QUADS)
            shader.uploadFloat(Uniforms.TERRAIN_SKIRT_DROP, maxOf(2f, side / 32f))
            cmd.setVertexInput(slot.inputs[0])
            cmd.drawIndexed(pool.lodIndexCounts[0])
            lastDrawn++
        }
        if (colour) shader.uploadInt(Uniforms.TERRAIN_FAR_TILE, 0)
        EngineStats.terrainFarTiles.addAndGet(lastDrawn)
    }

    /** The cached slot of [tile], building its stream from the overview when new (evicting the oldest if full). */
    private fun slot(tile: FarTile, layout: TerrainLayout, overview: TerrainOverview): ChunkBufferPool.Slot? {
        tiles[tile]?.let { return it }
        if (tiles.size >= MAX_TILES) { val oldest = tiles.entries.first(); pool.release(oldest.value); tiles.remove(oldest.key) }
        val slot = pool.acquire()
        val data = tileData(tile, layout, overview)
        mesh.stream(data, staging)
        slot.stream.update(0L, staging)
        tiles[tile] = slot
        return slot
    }

    private fun uploadMacro(m: TerrainMacro) {
        macro?.close()
        val tex = device.createTexture(TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, m.width, m.height,
            sampler = SamplerDesc(Filter.LINEAR, Filter.LINEAR, Wrap.CLAMP_TO_EDGE)))
        val buffer = MemoryUtil.memAlloc(m.width * m.height * 4)
        try {
            for (p in 0 until m.width * m.height) { buffer.put(m.rgb[p * 3]).put(m.rgb[p * 3 + 1]).put(m.rgb[p * 3 + 2]).put(255.toByte()) }
            buffer.flip()
            tex.upload(0, 0, buffer)
        } finally { MemoryUtil.memFree(buffer) }
        macro = tex
        macroSource = m
    }

    override fun close() {
        pool.close()
        tiles.clear()
        macro?.close(); macro = null; macroSource = null
    }

    companion object {
        const val TILE_QUADS = 32
        const val MAX_TILES = 512

        /** A tile's heights (with apron) sampled from the overview, as chunk data for the mesh builder. */
        fun tileData(tile: FarTile, layout: TerrainLayout, overview: TerrainOverview): TerrainChunkData {
            val side = (1 shl tile.level) * layout.chunkSize
            val spacing = side / TILE_QUADS
            val cornerX = layout.chunkCornerX(tile.tx shl tile.level)
            val cornerZ = layout.chunkCornerZ(tile.tz shl tile.level)
            val stride = TILE_QUADS + 3
            val heights = FloatArray(stride * stride)
            for (j in -1..TILE_QUADS + 1) for (i in -1..TILE_QUADS + 1) {
                heights[(j + 1) * stride + (i + 1)] = overview.heightAt(cornerX + i * spacing, cornerZ + j * spacing)
            }
            return TerrainChunkData(tile.tx, tile.tz, TILE_QUADS, spacing, cornerX, cornerZ, heights)
        }
    }
}
