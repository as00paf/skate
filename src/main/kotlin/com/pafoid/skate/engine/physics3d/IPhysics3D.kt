package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.physics3d.constraints.IPhysicsConstraint
import org.joml.Vector3f

interface IPhysics3D {
    var debugEnabled: Boolean
    fun getGravity(): Vector3f
    fun setGravity(gravity: Vector3f)
    var contactListener: PhysicsContactListener?

    /** Number of rigid bodies currently in the world (GameObject bodies, standalone bodies and anchors). */
    val rigidBodyCount: Int

    /** Fraction of the closest ray hit, or null. Triggers are ignored. */
    fun raycastClosest(from: Vector3f, to: Vector3f, excludeBody: IPhysicsBody3D? = null): Float? =
        raycast(from, to, ALL_LAYERS, excludeBody)?.fraction

    /** Closest hit along the ray against bodies whose layer matches [layerMask]; triggers are ignored. */
    fun raycast(from: Vector3f, to: Vector3f, layerMask: Int = ALL_LAYERS, excludeBody: IPhysicsBody3D? = null): PhysicsHit?

    /** All hits along the ray, sorted from nearest to farthest. */
    fun raycastAll(from: Vector3f, to: Vector3f, layerMask: Int = ALL_LAYERS, excludeBody: IPhysicsBody3D? = null): List<PhysicsHit>

    /**
     * Sweeps a convex [shape] (box, capsule, cylinder or hull) from [from] to [to] and returns the first hit.
     * The hit point is the shape's center at the time of impact.
     */
    fun shapeCast(
        shape: ColliderShape,
        from: Vector3f,
        to: Vector3f,
        layerMask: Int = ALL_LAYERS,
        excludeBody: IPhysicsBody3D? = null,
    ): PhysicsHit?
    fun add(go: GameObject)

    /**
     * Moves [go]'s body to its Transform pose (position and rotation) and clears its velocities and forces, for poses
     * set outside the simulation (editor edits, undo, respawn). Adds the body if it is not in the world yet.
     * Scale changes need a rebuild: [remove] then [add].
     */
    fun teleport(go: GameObject)

    /** Adds an immovable body at [location] to the world, usable as the fixed end of a joint. */
    fun createStaticAnchor(location: Vector3f): IRigidBodyHandle

    /** Creates a standalone body (e.g. a ragdoll bone) that is not tied to a GameObject and adds it to the world. */
    fun createRigidBody(shape: ColliderShape, mass: Float): IRigidBodyHandle

    /** Removes a body created by [createRigidBody] or [createStaticAnchor] from the world. */
    fun removeRigidBody(handle: IRigidBodyHandle)
    fun remove(go: GameObject)
    fun addConstraint(constraint: IPhysicsConstraint)
    fun removeConstraint(constraint: IPhysicsConstraint)
    /** Advances the simulation by exactly one fixed step of [fixedDt] seconds. */
    fun step(fixedDt: Float)
    fun debugDraw(dt: Float)
    fun destroy()
}
