package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.StreamingFocus
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.terrain.streaming.ChunkSource
import com.pafoid.skate.engine.terrain.streaming.Focus
import com.pafoid.skate.engine.terrain.streaming.StreamingBudget
import com.pafoid.skate.engine.terrain.streaming.StreamingRings
import com.pafoid.skate.engine.terrain.streaming.TerrainStreamer
import org.joml.Vector3f
import java.util.IdentityHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool

/**
 * Drives terrain streaming (REN-28 §15.3): every frame, after [TerrainSystem] built the terrains, it gathers the
 * focuses (the active camera for the render rings, [StreamingFocus] entities for every ring, with their velocity for
 * the lookahead), converts them to each streamed terrain's local space and runs that terrain's [TerrainStreamer].
 * Terrains with `streaming` OFF (or small AUTO ones) are left alone: all their chunks are resident.
 *
 * @param cameraPosition world position of the active camera, null when there is none (headless server)
 * @param source builds a chunk on a worker for a terrain (stored record first, then the recipe)
 */
class TerrainStreamingSystem(
    private val cameraPosition: () -> Vector3f? = { null },
    private val executor: Executor = ForkJoinPool.commonPool(),
    private val source: (TerrainComponent) -> ChunkSource = { DEFAULT_SOURCE },
    var rings: StreamingRings = StreamingRings(),
    /** CPU budget for resident detail chunks (REN-28 §15.4: 256 MB High, 128 MB Low). */
    var cpuBudgetBytes: Long = 256L * 1024 * 1024,
    private val report: (String) -> Unit = {},
) : System(priority = ExecutionPriority.EARLY) {
    override val runsAfter get() = listOf(TerrainSystem::class)
    override val runsWhilePaused: Boolean get() = true

    private val terrains = query(TerrainComponent::class)
    private val focusEntities = query(StreamingFocus::class)
    private val streamers = IdentityHashMap<TerrainComponent, TerrainStreamer>()
    private val focuses = ArrayList<Focus>()
    private val pool = ArrayList<Focus>()
    private val lastPositions = IdentityHashMap<StreamingFocus, Vector3f>()
    private val worldFocus = ArrayList<Focus>()
    private val terrainPosition = Vector3f()
    private val scratch = Vector3f()
    private var budgetWarned = false

    /** The streamer of [terrain], if it streams (tests, prefetch callers, debug overlay). */
    fun streamer(terrain: TerrainComponent): TerrainStreamer? = streamers[terrain]

    override fun update(dt: Float) {
        gatherWorldFocuses(dt)
        var resident = 0; var queued = 0; var bytes = 0L
        val seen = java.util.Collections.newSetFromMap(IdentityHashMap<TerrainComponent, Boolean>())
        terrains.forEach { go ->
            val terrain = go.getComponent<TerrainComponent>() ?: return@forEach
            val runtime = terrain.runtime
            if (!terrain.enabled || !runtime.streamed || runtime.layout == null) return@forEach
            seen += terrain
            val streamer = streamers.getOrPut(terrain) { TerrainStreamer(runtime, executor, source(terrain)) }
            go.getComponent<Transform>()?.worldMatrix?.getTranslation(terrainPosition) ?: terrainPosition.zero()
            // Focuses in terrain-local space
            focuses.clear()
            for ((i, w) in worldFocus.withIndex()) {
                val f = if (i < pool.size) pool[i] else Focus().also { pool += it }
                focuses += f.set(w.x - terrainPosition.x, w.z - terrainPosition.z, w.vx, w.vz, w.physics, w.radiusScale, w.priority)
            }
            val layout = runtime.layout!!
            val bytesPerChunk = runtime.chunks.firstOrNull()?.approxBytes() ?: estimate(layout.chunkQuads)
            val fitted = StreamingBudget.fit(rings, layout.chunkSize, bytesPerChunk, cpuBudgetBytes)
            if (fitted.detail < rings.detail && !budgetWarned) {
                budgetWarned = true
                report("Terrain streaming: detail radius reduced from ${rings.detail} m to ${fitted.detail} m to fit the memory budget")
            }
            streamer.update(focuses, focuses.size, fitted)
            resident += streamer.residentCount; queued += streamer.queued; bytes += streamer.residentBytes
        }
        // Terrains removed, disabled or no longer streamed
        val it = streamers.entries.iterator()
        while (it.hasNext()) { val e = it.next(); if (e.key !in seen) { e.value.close(); it.remove() } }
        EngineStats.terrainResidentChunks.set(resident)
        EngineStats.terrainQueued.set(queued)
        EngineStats.terrainCpuBytes.set(bytes)
    }

    /** The camera (render rings only) and every enabled [StreamingFocus] (with velocity from its last position). */
    private fun gatherWorldFocuses(dt: Float) {
        worldFocus.clear()
        var used = 0
        fun next(): Focus = (if (used < focusPool.size) focusPool[used] else Focus().also { focusPool += it }).also { used++ }
        cameraPosition()?.let { worldFocus += next().set(it.x, it.z, physics = false) }
        val alive = java.util.Collections.newSetFromMap(IdentityHashMap<StreamingFocus, Boolean>())
        focusEntities.forEach { go ->
            val focus = go.getComponent<StreamingFocus>()?.takeIf { it.enabled } ?: return@forEach
            val transform = go.getComponent<Transform>() ?: return@forEach
            transform.worldMatrix.getTranslation(scratch)
            val last = lastPositions.getOrPut(focus) { Vector3f(scratch) }
            val vx = if (dt > 0f) (scratch.x - last.x) / dt else 0f
            val vz = if (dt > 0f) (scratch.z - last.z) / dt else 0f
            last.set(scratch)
            alive += focus
            worldFocus += next().set(scratch.x, scratch.z, vx, vz, focus.physics, focus.radiusScale, focus.priority)
        }
        lastPositions.keys.retainAll(alive)
    }

    private val focusPool = ArrayList<Focus>()

    override fun destroy() {
        streamers.values.forEach { it.close() }
        streamers.clear()
        super.destroy()
    }

    companion object {
        val DEFAULT_SOURCE = ChunkSource { g, cx, cz -> com.pafoid.skate.engine.terrain.TerrainChunkBuilder.build(g, cx, cz) }

        /**
         * The chunk source of [terrain] (REN-28 §15.2): the `.skterrain` region record when the asset has one (DATA
         * chunks, sculpted chunks of a procedural terrain), else the recipe. Stamps apply either way; runs on workers.
         */
        fun storedChunks(terrain: TerrainComponent): ChunkSource {
            val ref = terrain.asset.takeIf { it.isNotBlank() } ?: return DEFAULT_SOURCE
            return ChunkSource { g, cx, cz ->
                val chunk = com.pafoid.skate.engine.terrain.TerrainChunkBuilder.build(g, cx, cz)
                val stored = com.pafoid.skate.engine.assets.terrain.TerrainAssets.chunk(ref, cx, cz, g.layout.chunkQuads, g.layout.spacing)
                if (stored != null && stored.heights.size == chunk.heights.size) {
                    // As TerrainAssets.afterBuild applies a record (AST-38): heights, then painted biomes
                    stored.heights.copyInto(chunk.heights)
                    chunk.paintedBiomeIds = stored.paintedBiomeIds
                    com.pafoid.skate.engine.terrain.TerrainChunkBuilder.refreshDerived(g, chunk)
                }
                chunk
            }
        }

        /** Bytes of a chunk before any is loaded: heights with apron, splat, biome ids. */
        fun estimate(chunkQuads: Int): Long {
            val n = (chunkQuads + 1).toLong(); val a = (chunkQuads + 3).toLong()
            return a * a * 4 + n * n * 9
        }
    }
}
