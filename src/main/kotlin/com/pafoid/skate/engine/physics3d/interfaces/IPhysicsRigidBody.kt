package com.pafoid.skate.engine.physics3d.interfaces

import com.jme3.math.Quaternion
import com.jme3.math.Vector3f

/**
 * Interface for a physics rigid body that abstracts the underlying physics engine implementation.
 */
interface IPhysicsRigidBody {
    var mass: Float
    var friction: Float
    var isKinematic: Boolean
    
    fun setPhysicsLocation(location: Vector3f): IPhysicsRigidBody
    fun getPhysicsLocation(out: Vector3f?): Vector3f
    fun setPhysicsRotation(rotation: Quaternion): IPhysicsRigidBody
    fun getPhysicsRotation(out: Quaternion?): Quaternion
    fun setDamping(linear: Float, angular: Float): IPhysicsRigidBody
    fun setCcdMotionThreshold(threshold: Float)
    fun setCcdSweptSphereRadius(radius: Float)
    fun setLinearVelocity(velocity: Vector3f)
    fun setAngularVelocity(velocity: Vector3f)
    fun getLinearVelocity(out: Vector3f?): Vector3f
    fun getAngularVelocity(out: Vector3f?): Vector3f
    fun applyCentralForce(force: Vector3f)
    fun applyImpulse(impulse: Vector3f, relPos: Vector3f)
    fun applyTorqueImpulse(torque: Vector3f)
    fun applyForce(force: Vector3f, relPos: Vector3f)
    val collisionShape: ICollisionShape
}