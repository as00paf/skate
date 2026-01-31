package com.pafoid.skate.engine.scenes.components

import com.jme3.bullet.collision.PhysicsRayTestResult
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Vector3f

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
class SkateboardPhysics : Component() {
    // Suspension parameters (Real-world Meters)
    var suspensionRestLength = 0.08f // 8cm total height
    var stiffness = 600.0f          // Slightly stiffer
    var damping = 25.0f
    
    // Corner offsets for 4 raycasts (deck corners/wheels)
    // Real-world Skateboard: ~0.8m length, ~0.2m width
    // Wheelbase: ~0.35m
    private val offsets = arrayOf(
        Vector3f(-0.175f, -0.01f, -0.1f), // Front Left (Wheel position)
        Vector3f(-0.175f, -0.01f, 0.1f),  // Front Right
        Vector3f(0.175f, -0.01f, -0.1f),  // Back Left
        Vector3f(0.175f, -0.01f, 0.1f)    // Back Right
    )

    private lateinit var rb: com.pafoid.skate.engine.physics3d.IPhysicsBody3D
    private val worldUp = Vector3f(0f, 1f, 0f)
    var isGrounded = false
        private set

    override fun start() {
        rb = gameObject.getComponent(RigidBody3D::class.java) ?: throw IllegalStateException("SkateboardPhysics requires RigidBody3D")
    }

    override fun update(dt: Float) {
        val scene = SceneManager.getCurrentScene() ?: return
        val transform = gameObject.transform.toWorldMatrix()
        
        var groundedCount = 0
        offsets.forEach { offset ->
            // Calculate ray start and end in world space
            val rayStart = Vector3f(offset).mulProject(transform)
            
            // Ray direction is board-local down
            val localDown = Vector3f(0f, -1f, 0f)
            transform.transformDirection(localDown)
            
            val rayEnd = Vector3f(localDown).mul(suspensionRestLength).add(rayStart)
            
            val results = scene.physics3d.rayTest(rayStart, rayEnd)
            if (results.isNotEmpty()) {
                val closest = results.minByOrNull { it.hitFraction }!!
                applySuspensionForce(closest, rayStart, localDown)
                groundedCount++
            }
        }
        isGrounded = groundedCount > 0
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
        val compression = 1.0f - hit.hitFraction
        val currentLength = suspensionRestLength * hit.hitFraction
        
        // F = k * x (Spring)
        val springForce = compression * stiffness
        
        // Damping (approximate using velocity projection)
        // We'd need the velocity at the specific point for perfect damping
        // For now, simple damping on the spring force
        val forceMagnitude = springForce // Add damping logic here later
        
        val forceDir = Vector3f(localDown).negate() // Force pushes up
        val worldForce = forceDir.mul(forceMagnitude)
        
        // Apply force at the specific corner position
        rb.applyForce(worldForce, rayStart)
    }
}