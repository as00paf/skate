package com.pafoid.skate.engine.physics3d

import org.joml.Quaternionf
import org.joml.Vector3f

/** Backend-agnostic handle to a rigid body living inside an [IPhysics3D] world. */
interface IRigidBodyHandle {
    val mass: Float
    val linearDamping: Float
    val angularDamping: Float
    var friction: Float
    var isKinematic: Boolean
    var linearVelocity: Vector3f
    var angularVelocity: Vector3f
    fun setDamping(linear: Float, angular: Float)
    fun setAngularFactor(factor: Vector3f)
    fun applyCentralForce(force: Vector3f)
    fun applyImpulse(impulse: Vector3f, relPos: Vector3f)
    fun applyTorqueImpulse(torque: Vector3f)
    fun applyForce(force: Vector3f, relPos: Vector3f)
    fun getLocation(out: Vector3f): Vector3f
    fun setLocation(location: Vector3f)
    fun getRotation(out: Quaternionf): Quaternionf
    fun setRotation(rotation: Quaternionf)
}
