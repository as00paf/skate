package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.DestructionMode
import com.pafoid.skate.engine.physics3d.PhysicsBackend
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

/** A `MeshCollider3D.meshRef` model could not be loaded, so colliders using it were left out (PHY-24); once per reference. */
data class CollisionMeshUnavailable(val reference: String, val reason: String) : PhysicsEvent("physics.collision_mesh_unavailable")

/** The scene's physics world runs on [used] because [requested] could not start (e.g. missing Jolt natives), see PHY-17. */
data class PhysicsBackendFellBack(
    val requested: PhysicsBackend,
    val used: PhysicsBackend,
    val reason: String,
) : PhysicsEvent("physics.backend_fallback")

/**
 * [body] started touching the water held by [water] (a `WaterComponent` entity), PHY-16. Published once per body and
 * water, for bodies whose `RigidBody3D.buoyancy` is not `OFF`; for splash audio (AUD) and gameplay (GPL).
 */
data class WaterEntered(val water: GameObject, val body: GameObject) : PhysicsEvent("physics.water_entered")

/** [body] left the water held by [water], or the water or body went away while touching (PHY-16). */
data class WaterExited(val water: GameObject, val body: GameObject) : PhysicsEvent("physics.water_exited")

/**
 * [target]'s `DestructibleComponent` took [amount] damage of [type] (`"impact"`, or the caller's type) at [point], and
 * has [remaining] integrity left (PHY-29 §8). For crack sounds and hit feedback.
 */
data class DestructibleDamaged(
    val target: GameObject,
    val amount: Float,
    val remaining: Float,
    val point: Vector3f,
    val type: String,
) : PhysicsEvent("physics.destructible_damaged")

/**
 * [target] broke at [point] (PHY-29 §8), published once before it is removed or replaced, so listeners can still read
 * its Transform. [impulse] is the impact that broke it (0 for API damage), [surface] its sound and dust key, [mode] how
 * it broke and [pieces] how many objects replaced it (0 for a removal, 1 for a swap).
 */
data class Fractured(
    val target: GameObject,
    val point: Vector3f,
    val impulse: Float,
    val surface: String,
    val mode: DestructionMode,
    val pieces: Int,
) : PhysicsEvent("physics.fractured")
