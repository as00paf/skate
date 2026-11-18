package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.terrain.TerrainChunkData
import org.joml.Vector3f
import java.util.IdentityHashMap

/**
 * The physics side of one terrain (PHY-26, REN-28 §8, task 9): one static [ColliderShape.Heightfield] body per resident
 * chunk, built from the chunk's full-resolution samples ([TerrainChunkData.colliderHeights]) so bodies touch exactly
 * the LOD-0 surface. Each body sits at the terrain's world position plus the chunk corner; the terrain's rotation is
 * ignored (terrain is axis-aligned). Neighbouring chunks share their edge samples, so there is no seam.
 *
 * Not a serialized component: `PhysicsSystem` keeps one per terrain entity and calls [sync]. [TerrainComponent.collider]
 * turns it off.
 */
class HeightfieldCollider3D(private val physics: IPhysics3D) {
    private class ChunkBody(val chunk: TerrainChunkData, val shape: ColliderShape.Heightfield, val handle: IRigidBodyHandle)

    private val bodies = IdentityHashMap<TerrainChunkData, ChunkBody>()
    private val origin = Vector3f(Float.NaN)
    private var builtVersion = Long.MIN_VALUE
    private var seenVersion = Long.MIN_VALUE

    /** Chunk bodies currently in the world. */
    val bodyCount: Int get() = bodies.size

    /** Chunks rebuilt by the last [sync] (tests, stats). */
    var lastRebuilt = 0
        private set

    /**
     * Brings the chunk bodies in line with [terrain] at world [position]. Rebuilds when the runtime version changed or
     * a chunk is dirty, and then only the chunks whose collider heights differ (or that are new); moves every body when
     * the terrain moved. With [waitForStableEdits] (edit mode), a version that is still changing (a brush stroke in
     * progress) is applied only once it holds for one call, so colliders are rebuilt at brush release.
     */
    fun sync(terrain: TerrainComponent, position: Vector3f, waitForStableEdits: Boolean = false) {
        lastRebuilt = 0
        if (!terrain.collider || !terrain.enabled) return clear()
        val runtime = terrain.runtime
        val version = runtime.version
        val stable = version == seenVersion
        seenVersion = version
        val chunks = runtime.chunks
        val changed = version != builtVersion || chunks.any { it.dirty && bodies[it]?.matches(it) != true }
        if (changed && (!waitForStableEdits || stable || bodies.isEmpty())) rebuild(chunks, position)
        if (origin != position) move(position)
    }

    private fun ChunkBody.matches(chunk: TerrainChunkData, heights: FloatArray = chunk.colliderHeights()): Boolean =
        shape.samplesPerSide == chunk.chunkQuads + 1 && shape.spacing == chunk.spacing && shape.heights.contentEquals(heights)

    private fun rebuild(chunks: List<TerrainChunkData>, position: Vector3f) {
        val live = java.util.Collections.newSetFromMap(IdentityHashMap<TerrainChunkData, Boolean>())
        for (chunk in chunks) {
            live += chunk
            val heights = chunk.colliderHeights()
            val existing = bodies[chunk]
            if (existing != null && existing.matches(chunk, heights)) continue
            existing?.let { physics.removeRigidBody(it.handle) }
            val shape = ColliderShape.Heightfield(chunk.chunkQuads + 1, chunk.spacing, heights)
            val handle = physics.createRigidBody(shape, 0f).apply {
                friction = FRICTION
                setLocation(corner(chunk, position))
            }
            bodies[chunk] = ChunkBody(chunk, shape, handle)
            lastRebuilt++
        }
        val gone = bodies.keys.filter { it !in live }
        gone.forEach { physics.removeRigidBody(bodies.remove(it)!!.handle) }
        builtVersion = seenVersion
        origin.set(position)
    }

    private fun move(position: Vector3f) {
        bodies.values.forEach { it.handle.setLocation(corner(it.chunk, position)) }
        origin.set(position)
    }

    private fun corner(chunk: TerrainChunkData, position: Vector3f) =
        Vector3f(position.x + chunk.cornerX, position.y, position.z + chunk.cornerZ)

    /** Removes every chunk body (collider turned off, terrain removed, scene unloaded). */
    fun clear() {
        bodies.values.forEach { physics.removeRigidBody(it.handle) }
        bodies.clear()
        builtVersion = Long.MIN_VALUE
        origin.set(Float.NaN)
    }

    companion object {
        /** Terrain surface friction, the same on every backend. */
        const val FRICTION = 0.6f
    }
}
