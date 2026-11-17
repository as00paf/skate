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
import com.pafoid.skate.engine.terrain.TerrainMacro
import com.pafoid.skate.engine.terrain.TerrainWeights
import com.pafoid.skate.engine.ecs.components.TerrainLayer
import com.pafoid.skate.engine.render.material.MaterialAsset
import org.joml.Vector4f
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
class TerrainRenderer(
    private val device: () -> RhiDevice = RhiContext::require,
    /** Reads a layer's `.skmat` (null: missing). */
    private val material: (String) -> MaterialAsset? = { null },
    /** Decodes a layer texture to RGBA8 (null: missing). */
    private val decode: (String) -> RgbaImage? = { null },
    private val layerSize: Int = TerrainLayerArrays.DEFAULT_SIZE,
) {

    /** One drawable terrain this frame. */
    class Draw(val go: GameObject, val terrain: TerrainComponent, val position: Vector3f)

    class GpuChunk(val data: TerrainChunkData, val slot: ChunkBufferPool.Slot, val errors: FloatArray, val splatLayer: Int) {
        val min = Vector3f()
        val max = Vector3f()
        var lod = -1
    }

    private class Resident(val pool: ChunkBufferPool, val atlas: SplatAtlas) {
        val chunks = IdentityHashMap<TerrainChunkData, GpuChunk>()
        var version = -1L
        var layers: TerrainLayerArrays? = null
        var layersKey: List<TerrainLayer>? = null
    }

    /** Biome colours, layer tilings and flags uploaded per terrain (reused). */
    private val vec4 = Vector4f()
    private val vec3 = Vector3f()

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
            resident = Resident(
                ChunkBufferPool(TerrainMeshBuilder(chunkLayout.chunkQuads), d, vertexLayout),
                SplatAtlas(d, chunkLayout.chunkQuads, chunkLayout.chunkCount),
            )
            terrains[draw.terrain] = resident
        }
        if (resident.version != runtime.version || resident.chunks.keys.any { it.dirty }) {
            val current = runtime.chunks
            val keep = java.util.Collections.newSetFromMap(IdentityHashMap<TerrainChunkData, Boolean>()).apply { addAll(current) }
            val it = resident.chunks.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                if (e.key !in keep) { resident.pool.release(e.value.slot); resident.atlas.release(e.value.splatLayer); it.remove() }
            }
            for (data in current) {
                val existing = resident.chunks[data]
                if (existing != null && !data.dirty) continue
                val slot = existing?.slot ?: resident.pool.acquire()
                resident.pool.upload(slot, data)
                val layer = when {
                    data.splat == null -> { existing?.let { resident.atlas.release(it.splatLayer) }; -1 }
                    existing != null && existing.splatLayer >= 0 -> existing.splatLayer
                    else -> resident.atlas.acquire()
                }
                if (layer >= 0) resident.atlas.upload(layer, data)
                resident.chunks[data] = GpuChunk(data, slot, resident.pool.mesh.lodErrors(data), layer)
                data.dirty = false
            }
            resident.version = runtime.version
            refreshLayers(resident, draw.terrain)
            runtime.macro = TerrainMacro.bake(chunkLayout, current, resident.layers?.averageColors ?: TerrainLayerImages.DEFAULT_COLORS)
        }
        refreshLayers(resident, draw.terrain)
        for (c in resident.chunks.values) {
            val size = c.data.chunkQuads * c.data.spacing
            c.min.set(draw.position.x + c.data.cornerX, draw.position.y + c.data.minHeight, draw.position.z + c.data.cornerZ)
            c.max.set(c.min.x + size, draw.position.y + c.data.maxHeight, c.min.z + size)
        }
        return resident.chunks.values
    }

    /** Rebuilds the layer texture arrays when the terrain's layers changed. */
    private fun refreshLayers(resident: Resident, terrain: TerrainComponent) {
        val key = terrain.layers.toList()
        if (resident.layers != null && key == resident.layersKey) return
        resident.layers?.close()
        resident.layers = TerrainLayerArrays(device(), TerrainLayerImages.buildAll(key, layerSize, material, decode), layerSize)
        resident.layersKey = key
    }

    /**
     * Binds [draw]'s material textures (splat atlas, layer arrays) on units 0–4 and uploads the per-terrain material
     * uniforms (tilings, triplanar flags, biome colours, debug view) to [shader] (bound, colour pass).
     */
    fun bindMaterials(shader: Shader, draw: Draw) {
        val resident = terrains[draw.terrain] ?: return
        val layers = resident.layers ?: return
        val cmd = device().commandList()
        cmd.setTexture(UNIT_SPLAT_0, resident.atlas.first); cmd.setTexture(UNIT_SPLAT_1, resident.atlas.second)
        cmd.setTexture(UNIT_ALBEDO, layers.albedo); cmd.setTexture(UNIT_NORMAL, layers.normal); cmd.setTexture(UNIT_ORM, layers.orm)
        shader.uploadInt(Uniforms.TERRAIN_SPLAT_0, UNIT_SPLAT_0); shader.uploadInt(Uniforms.TERRAIN_SPLAT_1, UNIT_SPLAT_1)
        shader.uploadInt(Uniforms.TERRAIN_ALBEDO, UNIT_ALBEDO); shader.uploadInt(Uniforms.TERRAIN_NORMAL, UNIT_NORMAL)
        shader.uploadInt(Uniforms.TERRAIN_ORM, UNIT_ORM)
        shader.uploadFloat(Uniforms.TERRAIN_CHUNK_QUADS, resident.pool.mesh.chunkQuads.toFloat())
        val l = draw.terrain.layers
        fun tiling(i: Int) = l.getOrNull(i)?.tilingMetres?.coerceAtLeast(0.01f) ?: 4f
        fun tri(i: Int) = if (l.getOrNull(i)?.triplanar == true) 1f else 0f
        shader.uploadVec4f(Uniforms.TERRAIN_TILING_A, vec4.set(tiling(0), tiling(1), tiling(2), tiling(3)))
        shader.uploadVec4f(Uniforms.TERRAIN_TILING_B, vec4.set(tiling(4), tiling(5), tiling(6), tiling(7)))
        shader.uploadVec4f(Uniforms.TERRAIN_TRIPLANAR_A, vec4.set(tri(0), tri(1), tri(2), tri(3)))
        shader.uploadVec4f(Uniforms.TERRAIN_TRIPLANAR_B, vec4.set(tri(4), tri(5), tri(6), tri(7)))
        shader.uploadInt(Uniforms.TERRAIN_BIOME_DEBUG, if (TerrainDebug.showBiomes) 1 else 0)
        for (b in 0 until TerrainWeights.LAYERS) {
            val c = draw.terrain.biomes.getOrNull(b)?.debugColor
            shader.uploadVec3f(Uniforms.TERRAIN_BIOME_COLORS[b], if (c != null && c.size >= 3) vec3.set(c[0], c[1], c[2]) else vec3.set(1f, 0f, 1f))
        }
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
            if (!shadow && fixedLod < 0) shader.uploadInt(Uniforms.TERRAIN_SPLAT_LAYER, c.splatLayer)
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

    private fun release(resident: Resident) {
        resident.pool.close(); resident.atlas.close(); resident.layers?.close(); resident.chunks.clear()
    }

    fun destroy() {
        terrains.values.forEach(::release)
        terrains.clear()
        layout?.close(); layout = null
        colourPipelines.close(); shadowPipelines.close()
    }

    companion object {
        /** Texture units of the terrain colour pass: material slots meshes use, free while terrain draws. */
        const val UNIT_SPLAT_0 = 0
        const val UNIT_SPLAT_1 = 1
        const val UNIT_ALBEDO = 2
        const val UNIT_NORMAL = 3
        const val UNIT_ORM = 4

        /** Skirts hang below the chunk's lowest sample; the culling box extends down by this much. */
        const val SKIRT_MARGIN = 8f
    }
}

/** Terrain debug views (REN-28 §6): the biome view tints chunks by dominant biome; the editor toggles it. */
object TerrainDebug {
    @Volatile
    var showBiomes = false
}
