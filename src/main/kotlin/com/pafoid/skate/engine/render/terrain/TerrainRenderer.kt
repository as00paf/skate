package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.data.VertexLayouts
import com.pafoid.skate.engine.render.lod.ScreenErrorLod
import com.pafoid.skate.engine.render.renderer.RenderTraversal
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiVertexLayout
import com.pafoid.skate.engine.render.state.PipelineCache
import com.pafoid.skate.engine.render.state.PipelineStates
import com.pafoid.skate.engine.terrain.TerrainChunkData
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.FrustumIntersection
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.IdentityHashMap

/**
 * GPU side of every [TerrainComponent] (REN-28 §4.1): uploads resident chunks into pooled slots (again when a chunk
 * is replaced or marked dirty), keeps each chunk's world AABB, LOD errors and current LOD, and draws the visible chunks
 * for the colour, picking and shadow passes.
 */
class TerrainRenderer(private val device: () -> RhiDevice = RhiContext::require) {

    /** One drawable terrain this frame. */
    class Draw(val go: GameObject, val terrain: TerrainComponent, val position: Vector3f)

    class GpuChunk(val data: TerrainChunkData, val slot: ChunkBufferPool.Slot, val errors: FloatArray) {
        val min = Vector3f()
        val max = Vector3f()
        var lod = -1
    }

    private class Resident(val pool: ChunkBufferPool) {
        val chunks = IdentityHashMap<TerrainChunkData, GpuChunk>()
        var version = -1L
    }

    private val terrains = IdentityHashMap<TerrainComponent, Resident>()
    private var layout: RhiVertexLayout? = null
    private val colourPipelines = PipelineCache(PipelineStates.TERRAIN, device)
    private val shadowPipelines = PipelineCache(PipelineStates.SHADOW, device)
    private val frustum = FrustumIntersection()
    private val viewProjection = Matrix4f()
    private val corner = Vector3f()
    private val seen = java.util.Collections.newSetFromMap(IdentityHashMap<TerrainComponent, Boolean>())

    /** Chunks drawn and culled by the last [drawChunks] calls since [resetCounters]. */
    var chunksDrawn = 0
        private set
    var chunksCulled = 0
        private set

    fun resetCounters() { chunksDrawn = 0; chunksCulled = 0 }

    /** Enabled, rendered terrains whose chunks are built, in scene order. */
    fun collect(root: GameObject, out: MutableList<Draw>) {
        out.clear()
        RenderTraversal.forEachRendered(root) { go ->
            val terrain = go.getComponent<TerrainComponent>()?.takeIf { it.enabled && it.runtime.layout != null } ?: return@forEachRendered
            val transform = go.getComponent<Transform>()?.takeIf { it.enabled } ?: return@forEachRendered
            out += Draw(go, terrain, transform.worldMatrix.getTranslation(Vector3f()))
        }
    }

    /** Uploads new or dirty chunks of [draw]'s terrain, releases replaced ones and refreshes the world AABBs. */
    fun sync(draw: Draw): Collection<GpuChunk> {
        val runtime = draw.terrain.runtime
        val chunkLayout = runtime.layout ?: return emptyList()
        var resident = terrains[draw.terrain]
        if (resident == null || resident.pool.mesh.chunkQuads != chunkLayout.chunkQuads) {
            resident?.let { release(it) }
            val d = device()
            val vertexLayout = layout ?: d.createVertexLayout(VertexLayouts.TERRAIN).also { layout = it }
            resident = Resident(ChunkBufferPool(TerrainMeshBuilder(chunkLayout.chunkQuads), d, vertexLayout))
            terrains[draw.terrain] = resident
        }
        if (resident.version != runtime.version || resident.chunks.keys.any { it.dirty }) {
            val current = runtime.chunks
            val keep = java.util.Collections.newSetFromMap(IdentityHashMap<TerrainChunkData, Boolean>()).apply { addAll(current) }
            val it = resident.chunks.entries.iterator()
            while (it.hasNext()) { val e = it.next(); if (e.key !in keep) { resident.pool.release(e.value.slot); it.remove() } }
            for (data in current) {
                val existing = resident.chunks[data]
                if (existing != null && !data.dirty) continue
                val slot = existing?.slot ?: resident.pool.acquire()
                resident.pool.upload(slot, data)
                resident.chunks[data] = GpuChunk(data, slot, resident.pool.mesh.lodErrors(data))
                data.dirty = false
            }
            resident.version = runtime.version
        }
        for (c in resident.chunks.values) {
            val size = c.data.chunkQuads * c.data.spacing
            c.min.set(draw.position.x + c.data.cornerX, draw.position.y + c.data.minHeight, draw.position.z + c.data.cornerZ)
            c.max.set(c.min.x + size, draw.position.y + c.data.maxHeight, c.min.z + size)
        }
        return resident.chunks.values
    }

    /** Drops GPU data of terrains not passed to [retain] (removed or disabled terrains). */
    fun retain(draws: List<Draw>) {
        seen.clear()
        draws.forEach { seen += it.terrain }
        val it = terrains.entries.iterator()
        while (it.hasNext()) { val e = it.next(); if (e.key !in seen) { release(e.value); it.remove() } }
    }

    fun setFrustum(projection: Matrix4f, view: Matrix4f) { frustum.set(viewProjection.set(projection).mul(view)) }
    fun setFrustum(viewProjection: Matrix4f) { frustum.set(viewProjection) }

    /**
     * Draws the visible [chunks] of [draw] with [shader] (bound, frame uniforms uploaded). LOD per chunk from the
     * screen error at its nearest point to [camera], plus [lodBias] (shadows draw one coarser); [fixedLod] forces a
     * level (picking). [shadow] uses the depth-only pipeline.
     */
    fun drawChunks(
        shader: Shader, draw: Draw, chunks: Collection<GpuChunk>, camera: Vector3f, projectionScale: Float,
        lodBias: Int = 0, fixedLod: Int = -1, shadow: Boolean = false,
    ) {
        val vertexLayout = layout ?: return
        val pipeline = (if (shadow) shadowPipelines else colourPipelines).get(shader, vertexLayout) ?: return
        val pool = terrains[draw.terrain]?.pool ?: return
        val cmd = device().commandList()
        cmd.setPipeline(pipeline)
        shader.uploadFloat(Uniforms.TERRAIN_SPACING, draw.terrain.runtime.layout?.spacing ?: 1f)
        for (c in chunks) {
            if (!frustum.testAab(c.min.x, c.min.y - SKIRT_MARGIN, c.min.z, c.max.x, c.max.y, c.max.z)) { chunksCulled++; continue }
            val lod = when {
                fixedLod >= 0 -> fixedLod
                else -> {
                    val selected = TerrainLod.select(c.errors, distanceTo(c, camera), projectionScale, c.lod)
                    if (!shadow) c.lod = selected
                    selected + lodBias
                }
            }.coerceIn(0, pool.mesh.lodCount - 1)
            shader.uploadVec3f(Uniforms.TERRAIN_CORNER, corner.set(c.min.x, draw.position.y, c.min.z))
            shader.uploadFloat(Uniforms.TERRAIN_SKIRT_DROP, TerrainLod.skirtDrop(c.errors, lod))
            cmd.setVertexInput(c.slot.inputs[lod])
            cmd.drawIndexed(pool.lodIndexCounts[lod])
            chunksDrawn++
            EngineStats.terrainTriangles.addAndGet(pool.lodIndexCounts[lod] / 3)
        }
    }

    /** Distance from [camera] to the nearest point of [c]'s box. */
    fun distanceTo(c: GpuChunk, camera: Vector3f): Float {
        val dx = maxOf(c.min.x - camera.x, 0f, camera.x - c.max.x)
        val dy = maxOf(c.min.y - camera.y, 0f, camera.y - c.max.y)
        val dz = maxOf(c.min.z - camera.z, 0f, camera.z - c.max.z)
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun projectionScale(viewportHeight: Int, fovDegrees: Float) = ScreenErrorLod.projectionScale(viewportHeight.coerceAtLeast(1), fovDegrees)

    private fun release(resident: Resident) { resident.pool.close(); resident.chunks.clear() }

    fun destroy() {
        terrains.values.forEach(::release)
        terrains.clear()
        layout?.close(); layout = null
        colourPipelines.close(); shadowPipelines.close()
    }

    companion object {
        /** Skirts hang below the chunk's lowest sample; the culling box extends down by this much. */
        const val SKIRT_MARGIN = 8f
    }
}
