package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f

/** One wheel of a [VehicleComponent]; [offset] is the suspension mount in chassis space (+Y up, +Z forward, +X right). */
@Serializable
data class WheelConfig(
    @Contextual val offset: Vector3f = Vector3f(),
    var radius: Float = 0.35f,
    var steerable: Boolean = false,
    var driven: Boolean = false,
)

/** What a wheel did during the last fixed step; read-only, for visuals and audio. */
class WheelState {
    var grounded = false
    var compression = 0f
    var steerAngle = 0f
    var load = 0f
}

/**
 * Raycast vehicle: a [RigidBody3D] chassis held up by spring/damper rays, driven and steered by forces at the wheels.
 * Works with any physics backend (only ray queries and impulses are used). Game code sets [throttle], [steering] and
 * [brake] every frame; `PhysicsSystem` applies the forces each fixed step. Forces are in newtons, angles in radians.
 */
@Serializable
data class VehicleComponent(
    val wheels: MutableList<WheelConfig> = mutableListOf(
        WheelConfig(Vector3f(-0.8f, -0.3f, 1.2f), steerable = true),
        WheelConfig(Vector3f(0.8f, -0.3f, 1.2f), steerable = true),
        WheelConfig(Vector3f(-0.8f, -0.3f, -1.2f), driven = true),
        WheelConfig(Vector3f(0.8f, -0.3f, -1.2f), driven = true),
    ),
    var suspensionRestLength: Float = 0.4f,
    var springStiffness: Float = 20000f,
    var springDamping: Float = 2500f,
    var maxSuspensionForce: Float = 40000f,
    var maxEngineForce: Float = 6000f,
    var maxBrakeForce: Float = 8000f,
    var maxSteerAngle: Float = 0.5f,
    var tyreFriction: Float = 1.2f,
) : Component() {
    /** -1 (reverse) to 1 (full forward). */
    @Transient
    var throttle = 0f

    /** -1 (left) to 1 (right). */
    @Transient
    var steering = 0f

    /** 0 to 1. */
    @Transient
    var brake = 0f

    @Transient
    var wheelStates: List<WheelState> = emptyList()
        internal set
}
