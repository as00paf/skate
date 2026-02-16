package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.ecs.GameObject
import org.joml.Vector3f

interface IPhysics3D {
    var debugEnabled: Boolean
    fun getGravity(): Vector3f
    fun setGravity(gravity: Vector3f)
    fun raycastClosest(from: Vector3f, to: Vector3f, excludeBody: IPhysicsBody3D? = null): RayTestResult?
    fun add(go: GameObject)
    fun update(go: GameObject)
    fun remove(go: GameObject)
    fun update(dt: Float)
    fun destroy()
}
