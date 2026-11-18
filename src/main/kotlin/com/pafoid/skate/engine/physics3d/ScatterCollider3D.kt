package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.terrain.ScatterInstance
import com.pafoid.skate.engine.terrain.ScatterPlacer
import com.pafoid.skate.engine.terrain.ScatterRule
import com.pafoid.skate.engine.terrain.TerrainChunkData
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.IdentityHashMap

/**
 * Static colliders for a terrain's scatter items (PHY-27, REN-28 §7, task 7): every item of a rule with
 * `collider = true` and a `model` gets one static box body, placed like the rendered instance
 * (`TerrainScatterRenderer.matrixOf`: on the ground, tilted by `alignToNormal`, turned by its yaw, scaled). Prefab rules
 * are skipped: their entities bring their own colliders.
 *
 * The box is fitted once per model ([fit]): its bounding box, or for a tree-like model (a canopy much wider than its
 * base) the trunk footprint over the full height, so the character walks under branches and stops at the trunk. Boxes
 * work on every backend, `SimplePhysics3D` included.
 *
 * Driven by `PhysicsSystem` next to [HeightfieldCollider3D]; a chunk's bodies are rebuilt when its `scatter` list or the
 * terrain's rule list is replaced, and all of them when the terrain moves.
 */
class ScatterCollider3D(
    private val physics: IPhysics3D,
    /** Triangles of a rule's model; null when unavailable (that rule then has no colliders). */
    private val meshOf: (String) -> ColliderShape.TriangleMesh? = CollisionMeshes::get,
) {
    /** A box in the model's own frame. */
    class FittedBox(val center: Vector3f, val halfExtents: Vector3f)

    private class ChunkBodies(val scatter: List<ScatterInstance>, val rules: List<ScatterPlacer.Entry>, val handles: List<IRigidBodyHandle>)

    private val chunks = IdentityHashMap<TerrainChunkData, ChunkBodies>()
    private val boxes = HashMap<String, FittedBox?>()
    private val origin = Vector3f(Float.NaN)

    /** Scatter bodies currently in the world. */
    val bodyCount: Int get() = chunks.values.sumOf { it.handles.size }

    /** Chunks whose bodies the last [sync] rebuilt (tests, stats). */
    var lastRebuilt = 0
        private set

    fun sync(terrain: TerrainComponent, position: Vector3f) {
        lastRebuilt = 0
        if (!terrain.collider || !terrain.enabled) return clear()
        val rules = terrain.runtime.generator?.scatterEntries.orEmpty()
        val moved = origin != position
        if (moved) origin.set(position)
        val resident = terrain.runtime.chunks
        val live = java.util.Collections.newSetFromMap(IdentityHashMap<TerrainChunkData, Boolean>())
        for (chunk in resident) {
            live += chunk
            val built = chunks[chunk]
            if (!moved && built != null && built.scatter === chunk.scatter && built.rules === rules) continue
            built?.handles?.forEach(physics::removeRigidBody)
            chunks[chunk] = ChunkBodies(chunk.scatter, rules, build(chunk.scatter, rules, position))
            lastRebuilt++
        }
        chunks.keys.filter { it !in live }.forEach { gone -> chunks.remove(gone)!!.handles.forEach(physics::removeRigidBody) }
    }

    private fun build(items: List<ScatterInstance>, rules: List<ScatterPlacer.Entry>, position: Vector3f): List<IRigidBodyHandle> {
        if (items.isEmpty()) return emptyList()
        val out = ArrayList<IRigidBodyHandle>()
        val rotation = Quaternionf()
        val center = Vector3f()
        for (item in items) {
            val rule = rules.getOrNull(item.rule)?.rule ?: continue
            if (!rule.collider || rule.model.isEmpty()) continue
            val box = boxes.getOrPut(rule.model) { meshOf(rule.model)?.let(::fit) } ?: continue
            placement(item, rule, rotation)
            rotation.transform(center.set(box.center).mul(item.scale)).add(position.x + item.x, position.y + item.y, position.z + item.z)
            out += physics.createRigidBody(ColliderShape.Box(Vector3f(box.halfExtents).mul(item.scale)), 0f).apply {
                friction = HeightfieldCollider3D.FRICTION
                setRotation(rotation)
                setLocation(center)
            }
        }
        return out
    }

    /** Removes every scatter body (collider off, terrain removed, scene unloaded). */
    fun clear() {
        chunks.values.forEach { it.handles.forEach(physics::removeRigidBody) }
        chunks.clear()
        origin.set(Float.NaN)
    }

    /** Forgets fitted boxes so models are read again (e.g. after a model file changed). */
    fun invalidateModels() {
        boxes.clear()
        chunks.values.forEach { it.handles.forEach(physics::removeRigidBody) }
        chunks.clear()
    }

    companion object {
        /** Vertices this low (fraction of the model height) form the base footprint. */
        const val BASE_FRACTION = 0.25f
        /** A full footprint this many times the base area marks a canopy: only the trunk collides. */
        const val CANOPY_RATIO = 4f
        private val UP = Vector3f(0f, 1f, 0f)

        /**
         * Rotation of a scattered item, as `TerrainScatterRenderer.matrixOf`: tilted towards the ground normal by
         * `alignToNormal`, then turned by the item's yaw.
         */
        fun placement(item: ScatterInstance, rule: ScatterRule, out: Quaternionf): Quaternionf {
            val normal = Vector3f(item.nx, item.ny, item.nz)
            return out.identity().rotationTo(UP, normal).nlerp(Quaternionf(), 1f - rule.alignToNormal.coerceIn(0f, 1f))
                .rotateY(item.yaw)
        }

        /** The collision box of a model, see the class notes; null for a model without vertices. */
        fun fit(mesh: ColliderShape.TriangleMesh): FittedBox? {
            val vertices = mesh.vertices
            if (vertices.isEmpty()) return null
            val min = Vector3f(Float.MAX_VALUE)
            val max = Vector3f(-Float.MAX_VALUE)
            vertices.forEach { min.min(it); max.max(it) }
            val baseTop = min.y + (max.y - min.y) * BASE_FRACTION
            var bx0 = Float.MAX_VALUE; var bx1 = -Float.MAX_VALUE
            var bz0 = Float.MAX_VALUE; var bz1 = -Float.MAX_VALUE
            vertices.forEach {
                if (it.y <= baseTop) {
                    bx0 = minOf(bx0, it.x); bx1 = maxOf(bx1, it.x)
                    bz0 = minOf(bz0, it.z); bz1 = maxOf(bz1, it.z)
                }
            }
            val fullArea = (max.x - min.x) * (max.z - min.z)
            val baseArea = (bx1 - bx0) * (bz1 - bz0)
            if (baseArea > 0f && fullArea > CANOPY_RATIO * baseArea) {
                min.x = bx0; max.x = bx1; min.z = bz0; max.z = bz1
            }
            val half = Vector3f(max).sub(min).mul(0.5f).max(Vector3f(MIN_HALF_EXTENT))
            return FittedBox(Vector3f(min).add(max).mul(0.5f), half)
        }

        private const val MIN_HALF_EXTENT = 0.02f
    }
}
