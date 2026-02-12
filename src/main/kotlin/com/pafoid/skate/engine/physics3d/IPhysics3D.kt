package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.collision.PhysicsRayTestResult
import com.pafoid.skate.engine.ecs.GameObject
import org.joml.Vector3f

interface IPhysics3D {
    var debugEnabled: Boolean
    fun getGravity(): Vector3f
    fun setGravity(gravity: Vector3f)
    fun rayTest(from: Vector3f, to: Vector3f): List<PhysicsRayTestResult>
    fun raycastClosest(from: Vector3f, to: Vector3f): PhysicsRayTestResult?
    fun add(go: GameObject)
    fun update(go: GameObject)
    fun remove(go: GameObject)
    fun update(dt: Float)
    fun destroy()
}
