package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.ecs.GameObject
import org.joml.Vector3f

sealed class PhysicsEvent(eventName: String) : Event(eventName)

data class Landing(val velocity: Vector3f, val impactForce: Float) : PhysicsEvent("physics.landing")
data class Takeoff(val velocity: Vector3f) : PhysicsEvent("physics.takeoff")
data class GroundedStateChanged(val isGrounded: Boolean) : PhysicsEvent("physics.grounded_changed")
data class Collision(val other: GameObject, val contactPoint: Vector3f, val normal: Vector3f) :
    PhysicsEvent("physics.collision")
