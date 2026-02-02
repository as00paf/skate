package com.pafoid.skate.engine.physics3d

import org.joml.Vector3f

interface IPhysicsBody3D {
    var linearVelocity: Vector3f
    var angularVelocity: Vector3f
    fun applyCentralForce(force: Vector3f)
    fun applyImpulse(impulse: Vector3f)
    fun applyTorqueImpulse(torque: Vector3f)
    fun applyForce(force: Vector3f, relPos: Vector3f)
    fun getVelocityInPoint(worldPos: Vector3f): Vector3f
}