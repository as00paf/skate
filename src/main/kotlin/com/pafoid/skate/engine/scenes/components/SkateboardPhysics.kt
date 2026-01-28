package com.pafoid.skate.engine.scenes.components

import com.jme3.bullet.collision.PhysicsRayTestResult
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.toWorldMatrix
import org.joml.Matrix4f
import org.joml.Vector3f

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

    @Transient private lateinit var rb: com.pafoid.skate.engine.physics3d.IPhysicsBody3D
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