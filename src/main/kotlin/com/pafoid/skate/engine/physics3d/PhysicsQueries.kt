package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.ecs.GameObject
import org.joml.Vector3f

/** Matches every collision layer. */
const val ALL_LAYERS = -1

/** Result of a raycast or shape sweep. [fraction] is the hit position along the query, from 0 (start) to 1 (end). */
data class PhysicsHit(
    val gameObject: GameObject?,
    val point: Vector3f,
    val normal: Vector3f,
    val fraction: Float,
)

/** Receives contact notifications produced while stepping the physics world. */
interface PhysicsContactListener {
    /** Called once when two non-trigger bodies start touching. */
    fun onCollisionStarted(a: GameObject, b: GameObject, point: Vector3f, normal: Vector3f, impulse: Float)

    fun onTriggerEnter(trigger: GameObject, other: GameObject)

    fun onTriggerExit(trigger: GameObject, other: GameObject)
}
