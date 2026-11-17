package com.pafoid.skate.engine.render.water

import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.RhiVertexLayout

/**
 * GPU copy of a [WaterGrid] (REN-29 §3.6): per chunk one vertex buffer and one indexed vertex input per LOD. Built for
 * one (size, quad size) key; the water renderer rebuilds it when the component's key changes.
 */
class WaterSurfaceMesh(val grid: WaterGrid, device: RhiDevice, layout: RhiVertexLayout) : AutoCloseable {
    class GpuChunk(val chunk: WaterChunk, val vertices: RhiBuffer, val indices: List<RhiBuffer>, val inputs: List<RhiVertexInput>) {
        fun indexCount(lod: Int): Int = chunk.lodIndices[lod].size
    }

    val chunks: List<GpuChunk> = grid.chunks.map { chunk ->
        val vbo = device.createBuffer(BufferUsage.VERTEX, BufferUpdate.STATIC, chunk.vertices)
        val ibos = chunk.lodIndices.map { device.createBuffer(BufferUsage.INDEX, BufferUpdate.STATIC, it) }
        GpuChunk(chunk, vbo, ibos, ibos.map { device.createVertexInput(layout, listOf(vbo), it) })
    }

    fun matches(sizeX: Float, sizeZ: Float, quadSize: Float): Boolean =
        grid.sizeX == sizeX && grid.sizeZ == sizeZ && grid.quadSize == quadSize.coerceAtLeast(WaterMeshBuilder.MIN_QUAD_SIZE)

    override fun close() {
        for (c in chunks) {
            c.inputs.forEach { it.close() }
            c.indices.forEach { it.close() }
            c.vertices.close()
        }
    }
}
