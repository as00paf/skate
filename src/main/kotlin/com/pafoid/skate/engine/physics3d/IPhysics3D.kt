package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.physics3d.constraints.IPhysicsConstraint
import org.joml.Vector3f

interface IPhysics3D {
    var debugEnabled: Boolean
    fun getGravity(): Vector3f
    fun setGravity(gravity: Vector3f)
    fun raycastClosest(from: Vector3f, to: Vector3f, excludeBody: IPhysicsBody3D? = null): Float?
    fun add(go: GameObject)

    /** Creates a standalone body (e.g. a ragdoll bone) that is not tied to a GameObject. */
    /** Adds an immovable body at [location] to the world, usable as the fixed end of a joint. */
    fun createStaticAnchor(location: Vector3f): IRigidBodyHandle

    fun createRigidBody(shape: ColliderShape, mass: Float): IRigidBodyHandle
    fun remove(go: GameObject)
    fun addConstraint(constraint: IPhysicsConstraint)
    fun removeConstraint(constraint: IPhysicsConstraint)
    /** Advances the simulation by exactly one fixed step of [fixedDt] seconds. */
    fun step(fixedDt: Float)
    fun debugDraw(dt: Float)
    fun destroy()
}
