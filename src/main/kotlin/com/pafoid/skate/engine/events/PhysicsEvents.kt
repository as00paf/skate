package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.ecs.GameObject
import org.joml.Vector3f

sealed class PhysicsEvent(eventName: String) : Event(eventName)

data class Landing(val velocity: Vector3f, val impactForce: Float) : PhysicsEvent("physics.landing")
data class Takeoff(val velocity: Vector3f) : PhysicsEvent("physics.takeoff")
data class GroundedStateChanged(val isGrounded: Boolean) : PhysicsEvent("physics.grounded_changed")
/** Published once per object when it starts touching [other]; [normal] points away from [other]. */
data class Collision(
    val self: GameObject,
    val other: GameObject,
    val contactPoint: Vector3f,
    val normal: Vector3f,
    val impulse: Float = 0f,
) : PhysicsEvent("physics.collision")

data class TriggerEntered(val trigger: GameObject, val other: GameObject) : PhysicsEvent("physics.trigger_entered")
data class TriggerExited(val trigger: GameObject, val other: GameObject) : PhysicsEvent("physics.trigger_exited")
