package com.pafoid.skate.game.skateboard

import com.jme3.bullet.collision.PhysicsRayTestResult
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.toWorldMatrix
import com.pafoid.skate.engine.ecs.systems.SceneManager
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

/**
 * Handles the physics simulation for a skateboard, primarily focused on the raycast suspension system.
 *
 * The suspension uses 4 raycasts (one at each wheel position) to simulate spring-damper behavior.
 * This approach (Hooke's Law) provides more stable behavior for skateboards than primitive colliders
 * because it allows for high-frequency impulses and realistic weight transfer.
 *
 * Physics parameters:
 * - [suspensionRestLength]: The maximum extension of the springs in meters.
 * - [stiffness]: The spring constant (k). Higher values mean stiffer suspension.
 * - [damping]: Resistance to oscillation. Prevents the board from bouncing indefinitely.
 */
class SkateboardPhysics : Component(), KoinComponent {

    private val sceneManager: SceneManager by inject()

    // Suspension parameters (Real-world Meters)
    var suspensionRestLength = 0.08f // 8cm total height
    var stiffness = 600.0f          // Slightly stiffer
    var damping = 25.0f
    var steeringCoefficient = 50.0f

    // Corner offsets for 4 raycasts (deck corners/wheels)
    // Real-world Skateboard: ~0.8m length, ~0.2m width
    // Wheelbase: ~0.35m
    private val offsets = arrayOf(
        Vector3f(-0.175f, -0.01f, -0.1f), // Front Left (Wheel position)
        Vector3f(-0.175f, -0.01f, 0.1f),  // Front Right
        Vector3f(0.175f, -0.01f, -0.1f),  // Back Left
        Vector3f(0.175f, -0.01f, 0.1f)    // Back Right
    )

    private lateinit var rb: IPhysicsBody3D
    private val worldUp = Vector3f(0f, 1f, 0f)
    var isGrounded = false
        private set

    override fun start() {
        rb = gameObject.getComponent<RigidBody3D>() ?: throw IllegalStateException("SkateboardPhysics requires RigidBody3D")
    }

    override fun update(dt: Float) {
        val scene = sceneManager.currentScene ?: return
        val transform = gameObject.getComponent<Transform>() ?: return
        val transformMatrix = transform.toWorldMatrix()

        var groundedCount = 0
        offsets.forEach { offset ->
            // Calculate ray start and end in world space
            val rayStart = Vector3f(offset).mulProject(transformMatrix)

            // Ray direction is board-local down
            val localDown = Vector3f(0f, -1f, 0f)
            transformMatrix.transformDirection(localDown)

            val rayEnd = Vector3f(localDown).mul(suspensionRestLength).add(rayStart)

            val results = scene.physics3d.rayTest(rayStart, rayEnd)
            if (results.isNotEmpty()) {
                val closest = results.minByOrNull { it.hitFraction }
                if (closest != null) {
                    applySuspensionForce(closest, rayStart, localDown)
                    groundedCount++
                }
            }
        }
        isGrounded = groundedCount > 0

        if (isGrounded) {
            applySteering(dt)
        }
    }

    private fun applySteering(dt: Float) {
        // Calculate Roll (Lean) relative to local forward
        // Local Right Vector in World Space
        val localRight = Vector3f(0f, 0f, 1f)
        val transform = gameObject.getComponent<Transform>() ?: return
        transform.toWorldMatrix().transformDirection(localRight)

        // Project Local Right onto World Up to get Roll component
        // Dot product gives the sine of the angle if vectors are normalized
        val roll = localRight.dot(worldUp)

        // Deadzone to prevent jitter steering
        if (abs(roll) < 0.05f) return

        // Steering Torque = -Roll * Speed * Coefficient
        // We steer around the LOCAL Y (Up) axis
        val speed = rb.linearVelocity.length()
        if (speed > 0.1f) {
            val torqueMagnitude = -roll * speed * steeringCoefficient

            val localUp = Vector3f(0f, 1f, 0f)
            transform.toWorldMatrix().transformDirection(localUp)

            val torque = Vector3f(localUp).mul(torqueMagnitude)
            rb.applyTorqueImpulse(torque.mul(dt))
        }
    }

    /**
     * Calculates and applies upward force based on spring compression.
     *
     * Uses Hooke's Law: F = k * x
     * where:
     * - F is the resulting force
     * - k is the [stiffness]
     * - x is the compression distance (restLength - currentLength)
     *
     * @param hit The raycast result containing hit fraction and normal.
     * @param rayStart The origin of the ray in world space, where the force is applied.
     * @param localDown The local downward vector of the board in world space.
     */
    private fun applySuspensionForce(hit: PhysicsRayTestResult, rayStart: Vector3f, localDown: Vector3f) {
        val compression = (1.0f - hit.hitFraction) * suspensionRestLength

        // F = k * x (Spring)
        val springForce = compression * stiffness

        // Damping: F = d * v
        // We project the point velocity onto the suspension axis (up)
        val localUp = Vector3f(localDown).negate()
        val pointVelocity = rb.getVelocityInPoint(rayStart)
        val vSuspension = pointVelocity.dot(localUp)
        val dampingForce = vSuspension * damping

        val forceMagnitude = (springForce - dampingForce).coerceAtLeast(0f)

        val worldForce = Vector3f(localUp).mul(forceMagnitude)

        // Apply force at the specific corner position
        rb.applyForce(worldForce, rayStart)
    }
}