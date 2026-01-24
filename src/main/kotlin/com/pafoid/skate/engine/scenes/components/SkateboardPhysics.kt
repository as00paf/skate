package com.pafoid.skate.engine.scenes.components

import com.jme3.bullet.collision.PhysicsRayTestResult
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.toMatrix
import org.joml.Matrix4f
import org.joml.Vector3f

class SkateboardPhysics : Component() {
    // Suspension parameters
    var suspensionRestLength = 0.5f
    var stiffness = 50.0f
    var damping = 5.0f
    
    // Corner offsets for 4 raycasts (deck corners)
    private val offsets = arrayOf(
        Vector3f(-1.4f, -0.05f, -0.4f), // Front Left
        Vector3f(-1.4f, -0.05f, 0.4f),  // Front Right
        Vector3f(1.4f, -0.05f, -0.4f),  // Back Left
        Vector3f(1.4f, -0.05f, 0.4f)    // Back Right
    )

    @Transient private lateinit var rb: RigidBody3D
    private val worldUp = Vector3f(0f, 1f, 0f)
    var isGrounded = false
        private set

    override fun start() {
        rb = gameObject.getComponent<RigidBody3D>() ?: throw IllegalStateException("SkateboardPhysics requires RigidBody3D")
    }

    override fun update(dt: Float) {
        val scene = SceneManager.getCurrentScene() ?: return
        val transform = gameObject.transform.toMatrix()
        
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
        rb.rawBody?.applyForce(
            com.jme3.math.Vector3f(worldForce.x, worldForce.y, worldForce.z),
            com.jme3.math.Vector3f(rayStart.x, rayStart.y, rayStart.z)
        )
    }
}