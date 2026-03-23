package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.ecs.GameObject
import org.joml.Vector3f

/**
 * Base class for all physics events.
 *
 * Physics events are published by [com.pafoid.skate.engine.ecs.systems.PhysicsSystem]
 * and [com.pafoid.skate.game.skateboard.SkateboardPhysics] when physics state changes.
 */
sealed class PhysicsEvent(eventName: String) : GameEvent(eventName)

/**
 * Published when an entity lands on the ground.
 *
 * @property velocity Velocity at landing (m/s)
 * @property impactForce Impact force (based on velocity and mass)
 */
data class Landing(
    val velocity: Vector3f,
    val impactForce: Float
) : PhysicsEvent("physics.landing")

/**
 * Published when an entity takes off from the ground.
 *
 * @property velocity Velocity at takeoff (m/s)
 */
data class Takeoff(val velocity: Vector3f) : PhysicsEvent("physics.takeoff")

/**
 * Published when grounded state changes.
 *
 * @property isGrounded True if entity is touching the ground
 */
data class GroundedStateChanged(val isGrounded: Boolean) : PhysicsEvent("physics.grounded_changed")

/**
 * Published when a collision is detected.
 *
 * @property other The other GameObject involved in the collision
 * @property contactPoint World space contact point
 * @property normal Contact normal (points away from collision surface)
 */
data class Collision(
    val other: GameObject,
    val contactPoint: Vector3f,
    val normal: Vector3f
) : PhysicsEvent("physics.collision")
