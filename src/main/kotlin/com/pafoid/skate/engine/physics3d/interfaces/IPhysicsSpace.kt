package com.pafoid.skate.engine.physics3d.interfaces

/**
 * Interface for a physics space that abstracts the underlying physics engine implementation.
 */
interface IPhysicsSpace {
    fun add(rigidBody: IPhysicsRigidBody)
    fun remove(rigidBody: IPhysicsRigidBody)
    fun update(dt: Float, maxSteps: Int)
    fun setGravity(gravity: com.jme3.math.Vector3f)
    fun getGravity(out: com.jme3.math.Vector3f?): com.jme3.math.Vector3f
    val rigidBodyList: java.util.List<IPhysicsRigidBody>
}