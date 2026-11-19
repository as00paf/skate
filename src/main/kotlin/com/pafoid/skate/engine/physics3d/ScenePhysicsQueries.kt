package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.ecs.GameObject
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * The scene queries gameplay may use (PHY-22, `docs/design/GPL-24.md` §4.3): rays, shape sweeps and overlaps against
 * the scene's physics world, whatever the backend. `PhysicsSystem.queries` provides it; systems such as `CombatSystem`
 * take this interface rather than `IPhysics3D`, so tests can hand them a fake.
 *
 * All three ignore triggers and take a [layerMask] and an optional body to skip (usually the caller's own).
 */
interface ScenePhysicsQueries {
    /** Closest hit along the segment, or null. */
    fun raycast(from: Vector3f, to: Vector3f, layerMask: Int = ALL_LAYERS, excludeBody: IPhysicsBody3D? = null): PhysicsHit?

    /** Every hit along the segment, nearest first. */
    fun raycastAll(from: Vector3f, to: Vector3f, layerMask: Int = ALL_LAYERS, excludeBody: IPhysicsBody3D? = null): List<PhysicsHit>

    /** First hit of a convex [shape] swept from [from] to [to]; the hit point is the shape centre at impact. */
    fun shapeCast(
        shape: ColliderShape,
        from: Vector3f,
        to: Vector3f,
        layerMask: Int = ALL_LAYERS,
        excludeBody: IPhysicsBody3D? = null,
    ): PhysicsHit?

    /** Scene objects inside or touching [shape] at [position]/[rotation], nearest first (see [IPhysics3D.overlap]). */
    fun overlap(
        shape: ColliderShape,
        position: Vector3f,
        rotation: Quaternionf = Quaternionf(),
        layerMask: Int = ALL_LAYERS,
        excludeBody: IPhysicsBody3D? = null,
    ): List<GameObject>

    companion object {
        /** The queries of [physics]. */
        fun of(physics: IPhysics3D): ScenePhysicsQueries = object : ScenePhysicsQueries {
            override fun raycast(from: Vector3f, to: Vector3f, layerMask: Int, excludeBody: IPhysicsBody3D?) =
                physics.raycast(from, to, layerMask, excludeBody)

            override fun raycastAll(from: Vector3f, to: Vector3f, layerMask: Int, excludeBody: IPhysicsBody3D?) =
                physics.raycastAll(from, to, layerMask, excludeBody)

            override fun shapeCast(shape: ColliderShape, from: Vector3f, to: Vector3f, layerMask: Int, excludeBody: IPhysicsBody3D?) =
                physics.shapeCast(shape, from, to, layerMask, excludeBody)

            override fun overlap(shape: ColliderShape, position: Vector3f, rotation: Quaternionf, layerMask: Int, excludeBody: IPhysicsBody3D?) =
                physics.overlap(shape, position, rotation, layerMask, excludeBody)
        }

        /** Orders overlap results nearest body origin first, each once. */
        internal fun nearestFirst(position: Vector3f, found: Map<GameObject, Vector3f>): List<GameObject> =
            found.entries.sortedBy { it.value.distanceSquared(position) }.map { it.key }

        /** Rejects shapes that cannot be an overlap query. */
        internal fun requireQueryShape(shape: ColliderShape) = require(shape !is ColliderShape.TriangleMesh && shape !is ColliderShape.Heightfield) {
            "Overlap queries need a convex or compound shape, not ${shape::class.simpleName}"
        }
    }
}
