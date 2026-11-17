package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.RhiVertexLayout

/**
 * Pooled per-chunk vertex streams (REN-28 §4.1): every slot owns a stream buffer sized for one chunk and one vertex
 * input per LOD over (shared grid, its stream, the shared LOD index buffer). Loading a chunk is an `update` into a free
 * slot, never a buffer creation once the pool is warm (the streaming hitch rule, §15.5). One pool per chunk size.
 */
class ChunkBufferPool(
    val mesh: TerrainMeshBuilder,
    private val device: RhiDevice,
    private val layout: RhiVertexLayout,
) : AutoCloseable {
    class Slot(val stream: RhiBuffer, val inputs: List<RhiVertexInput>)

    val grid: RhiBuffer = device.createBuffer(BufferUsage.VERTEX, BufferUpdate.STATIC, mesh.gridVertices())
    val lodIndices: List<RhiBuffer> = List(mesh.lodCount) { device.createBuffer(BufferUsage.INDEX, BufferUpdate.STATIC, mesh.lodIndices(it)) }
    val lodIndexCounts: IntArray = IntArray(mesh.lodCount) { mesh.lodIndices(it).size }

    private val free = ArrayDeque<Slot>()
    private val all = ArrayList<Slot>()
    private val staging = FloatArray(mesh.vertexCount * TerrainMeshBuilder.STREAM_FLOATS)

    val slotCount: Int get() = all.size
    val freeCount: Int get() = free.size

    fun acquire(): Slot = free.removeFirstOrNull() ?: run {
        val stream = device.createBuffer(BufferUsage.VERTEX, BufferUpdate.DYNAMIC, FloatArray(staging.size))
        Slot(stream, lodIndices.map { device.createVertexInput(layout, listOf(grid, stream), it) }).also { all += it }
    }

    fun release(slot: Slot) { free.addLast(slot) }

    /** Writes [data]'s stream into [slot]. */
    fun upload(slot: Slot, data: com.pafoid.skate.engine.terrain.TerrainChunkData) {
        mesh.stream(data, staging)
        slot.stream.update(0L, staging)
    }

    override fun close() {
        for (s in all) { s.inputs.forEach { it.close() }; s.stream.close() }
        all.clear(); free.clear()
        lodIndices.forEach { it.close() }
        grid.close()
    }
}
