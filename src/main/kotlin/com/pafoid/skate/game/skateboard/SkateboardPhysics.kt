package com.pafoid.skate.game.skateboard

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.toWorldMatrix
import com.pafoid.skate.engine.events.GroundedStateChanged
import com.pafoid.skate.engine.events.Landing
import com.pafoid.skate.engine.events.Takeoff
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.RayTestResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f
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
 * Publishes events:
 * - [Landing] when skateboard lands on ground
 * - [Takeoff] when skateboard takes off from ground
 * - [GroundedStateChanged] when grounded state changes
 *
 * Physics parameters:
 * - [suspensionRestLength]: The maximum extension of the springs in meters.
 * - [stiffness]: The spring constant (k). Higher values mean stiffer suspension.
 * - [damping]: Resistance to oscillation. Prevents the board from bouncing indefinitely.
 */
@Serializable
class SkateboardPhysics : Component(), KoinComponent {

    private val eventSystem: EventSystem by inject() // TODO : remove

    // Suspension parameters (Real-world Meters)
    var suspensionRestLength = 0.08f // 8cm total height
    var stiffness = 600.0f          // Slightly stiffer
    var damping = 25.0f
    var steeringCoefficient = 50.0f

    // Corner offsets for 4 raycasts (deck corners/wheels)
    // Real-world Skateboard: ~0.8m length, ~0.2m width
    // Wheelbase: ~0.35m
    @Transient private val offsets = arrayOf(
        Vector3f(-0.175f, -0.01f, -0.1f), // Front Left (Wheel position)
        Vector3f(-0.175f, -0.01f, 0.1f),  // Front Right
        Vector3f(0.175f, -0.01f, -0.1f),  // Back Left
        Vector3f(0.175f, -0.01f, 0.1f)    // Back Right
    )

    @Transient private lateinit var rb: IPhysicsBody3D
    @Transient private lateinit var cachedTransform: Transform
    @Transient private val worldUp = Vector3f(0f, 1f, 0f)

    // Reusable vectors to reduce garbage collection in hot loops
    @Transient private val rayStart = Vector3f()
    @Transient private val localDown = Vector3f()
    @Transient private val rayEnd = Vector3f()
    @Transient private val localRight = Vector3f()
    @Transient private val localUp = Vector3f()
    @Transient private val torque = Vector3f()
    @Transient private val pointVelocity = Vector3f()
    @Transient private val worldForce = Vector3f()

    var isGrounded = false
        private set

    // Track previous grounded state for event publishing
    private var wasGrounded = false

    // Fixed-timestep accumulator for physics (120Hz)
    private var updateAccumulator = 0f
    private val updateTimestep = 1f / 120f

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        rb = gameObject.getComponent<RigidBody3D>() ?: throw IllegalStateException("SkateboardPhysics requires RigidBody3D")
        cachedTransform = gameObject.getComponent<Transform>() ?: throw IllegalStateException("SkateboardPhysics requires Transform")
        wasGrounded = false
    }

    override fun update(dt: Float) {
        val worldMatrix = cachedTransform.toWorldMatrix()

        // Run physics logic at fixed 120Hz timestep
        updateAccumulator += dt
        while (updateAccumulator >= updateTimestep) {
            updateAccumulator -= updateTimestep
            runPhysicsStep(worldMatrix)
        }

        // Steering runs every frame for responsiveness
        if (isGrounded) {
            applySteering(dt, worldMatrix)
        }
    }

    /**
     * Runs the physics step at fixed timestep: grounded check and event publishing.
     */
    private fun runPhysicsStep(worldMatrix: Matrix4f) {
        //checkIfGrounded(worldMatrix)

        // Publish events on grounded state change
        if (isGrounded != wasGrounded) {
            eventSystem.publish(GroundedStateChanged(isGrounded))

            if (isGrounded) {
                // Landing - calculate impact force from velocity
                val velocity = rb.linearVelocity
                val impactForce = kotlin.math.abs(velocity.y) * 10f // Simplified impact calculation
                eventSystem.publish(Landing(velocity, impactForce))
            } else {
                // Takeoff
                val velocity = rb.linearVelocity
                eventSystem.publish(Takeoff(velocity))
            }

            wasGrounded = isGrounded
        }
    }

    /*private fun checkIfGrounded(worldMatrix: Matrix4f) {
        // Early exit: moving upward fast and already not grounded
        if (rb.linearVelocity.y > 2.0f && !isGrounded) return

        // Early exit: very high above ground — raycasts won't reach
        if (cachedTransform.translation.y > suspensionRestLength * 3) return

        val scene = sceneManager.currentScene ?: return

        var groundedCount = 0
        offsets.forEach { offset ->
            // Calculate ray start and end in world space (reuse vectors)
            rayStart.set(offset).mulProject(worldMatrix)

            // Ray direction is board-local down
            localDown.set(0f, -1f, 0f)
            worldMatrix.transformDirection(localDown)

            rayEnd.set(localDown).mul(suspensionRestLength).add(rayStart)
            val closest = scene.physics3d.raycastClosest(rayStart, rayEnd)
            if (closest != null) {
                applySuspensionForce(closest, rayStart, localDown)
                groundedCount++
            }
        }

        isGrounded = groundedCount > 0
    }*/

    private fun applySteering(dt: Float, worldMatrix: Matrix4f) {
        // Calculate Roll (Lean) relative to local forward
        // Local Right Vector in World Space
        localRight.set(0f, 0f, 1f)
        worldMatrix.transformDirection(localRight)

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

            localUp.set(0f, 1f, 0f)
            worldMatrix.transformDirection(localUp)

            torque.set(localUp).mul(torqueMagnitude)
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
     * @param rayStartVec The origin of the ray in world space, where the force is applied.
     * @param localDownVec The local downward vector of the board in world space.
     */
    private fun applySuspensionForce(hit: RayTestResult, rayStartVec: Vector3f, localDownVec: Vector3f) {
        val compression = (1.0f - hit.hitFraction) * suspensionRestLength

        // F = k * x (Spring)
        val springForce = compression * stiffness

        // Damping: F = d * v
        // We project the point velocity onto the suspension axis (up)
        localUp.set(localDownVec).negate()
        pointVelocity.set(rb.getVelocityInPoint(rayStartVec))
        val vSuspension = pointVelocity.dot(localUp)
        val dampingForce = vSuspension * damping

        val forceMagnitude = (springForce - dampingForce).coerceAtLeast(0f)

        worldForce.set(localUp).mul(forceMagnitude)

        // Apply force at the specific corner position
        rb.applyForce(worldForce, rayStartVec)
    }

}
