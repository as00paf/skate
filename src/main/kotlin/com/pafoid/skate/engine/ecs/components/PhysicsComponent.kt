package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * Component that stores physics state for an entity.
 *
 * This component provides a snapshot of physics state that can be read by gameplay systems
 * without directly coupling to the physics engine. The physics system writes to this component,
 * and gameplay systems like TrickDetector and PlayerController read from it.
 *
 * ## Properties
 *
 * - [linearVelocity]: Current linear velocity vector (m/s)
 * - [angularVelocity]: Current angular velocity vector (rad/s)
 * - [isMoving]: True when linear velocity magnitude exceeds threshold
 * - [speed]: Current speed magnitude (m/s)
 * - [isRotating]: True when angular velocity magnitude exceeds threshold
 *
 * ## Usage
 *
 * ```kotlin
 * val physics = gameObject.getComponent<PhysicsComponent>()
 * val speed = physics?.speed ?: 0f
 * if (physics?.isMoving == true) {
 *     // Entity is in motion
 * }
 * ```
 *
 * @see com.pafoid.skate.engine.physics3d.IPhysicsBody3D
 */
@Serializable
class PhysicsComponent : Component() {

    // =========================================================================
    // VELOCITY STATE
    // =========================================================================

    /**
     * Current linear velocity in world space (meters per second).
     * Updated by physics system each frame.
     */
    @Contextual
    var linearVelocity: Vector3f = Vector3f(0f, 0f, 0f)

    /**
     * Current angular velocity in world space (radians per second).
     * Updated by physics system each frame.
     */
    @Contextual
    var angularVelocity: Vector3f = Vector3f(0f, 0f, 0f)

    // =========================================================================
    // DERIVED STATE (computed from velocity)
    // =========================================================================

    /**
     * Current speed magnitude (m/s).
     * Computed from linearVelocity each frame.
     */
    var speed: Float = 0f

    /**
     * True when the entity is moving (speed > threshold).
     * Threshold: 0.01 m/s to avoid floating point noise.
     */
    var isMoving: Boolean = false

    /**
     * True when the entity is rotating (angular velocity > threshold).
     * Threshold: 0.01 rad/s to avoid floating point noise.
     */
    var isRotating: Boolean = false

    /**
     * Updates the component state from physics body properties.
     * Called by physics system each frame before gameplay systems read state.
     *
     * @param newLinearVelocity New linear velocity from physics body
     * @param newAngularVelocity New angular velocity from physics body
     */
    fun updateFromPhysics(newLinearVelocity: Vector3f, newAngularVelocity: Vector3f) {
        linearVelocity.set(newLinearVelocity)
        angularVelocity.set(newAngularVelocity)

        // Compute derived state
        speed = newLinearVelocity.length()
        isMoving = speed > 0.01f

        val angularSpeed = newAngularVelocity.length()
        isRotating = angularSpeed > 0.01f
    }

    /**
     * Resets all state to default values.
     * Called when component is initialized or when entity is reset.
     */
    fun reset() {
        linearVelocity.set(0f, 0f, 0f)
        angularVelocity.set(0f, 0f, 0f)
        speed = 0f
        isMoving = false
        isRotating = false
    }
}
