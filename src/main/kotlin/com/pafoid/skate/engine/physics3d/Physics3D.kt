package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.collision.shapes.CylinderCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.scenes.GameObject
import org.joml.Vector3f

class Physics3D {
    private val physicsSpace: PhysicsSpace = PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT)
    
    private var physicsTime = 0f
    private val physicsTimeStep = 1f / 60f

    init {
        physicsSpace.setGravity(com.jme3.math.Vector3f(0f, -9.81f, 0f))
    }

    fun getGravity(): Vector3f {
        val g = physicsSpace.getGravity(null)
        return Vector3f(g.x, g.y, g.z)
    }

    fun setGravity(gravity: Vector3f) {
        physicsSpace.setGravity(com.jme3.math.Vector3f(gravity.x, gravity.y, gravity.z))
    }

    fun rayTest(from: Vector3f, to: Vector3f): List<PhysicsRayTestResult> {
        val start = com.jme3.math.Vector3f(from.x, from.y, from.z)
        val end = com.jme3.math.Vector3f(to.x, to.y, to.z)
        return physicsSpace.rayTest(start, end)
    }

    fun add(go: GameObject) {
        val rb = go.getComponent<com.pafoid.skate.engine.physics3d.components.RigidBody3D>()
        if (rb != null && rb.rawBody == null) {
            val boxColliders = go.components.filterIsInstance<com.pafoid.skate.engine.physics3d.components.BoxCollider3D>()
            val cylinderColliders = go.components.filterIsInstance<com.pafoid.skate.engine.physics3d.components.CylinderCollider3D>()
            
            val compound = CompoundCollisionShape()
            
            boxColliders.forEach { c ->
                val shape = BoxCollisionShape(com.jme3.math.Vector3f(c.halfExtents.x, c.halfExtents.y, c.halfExtents.z))
                compound.addChildShape(shape, com.jme3.math.Vector3f(c.offset.x, c.offset.y, c.offset.z))
            }
            
            cylinderColliders.forEach { c ->
                val shape = CylinderCollisionShape(c.radius, c.height, c.axis)
                compound.addChildShape(shape, com.jme3.math.Vector3f(c.offset.x, c.offset.y, c.offset.z))
            }

            // If no colliders, provide a default box
            if (boxColliders.isEmpty() && cylinderColliders.isEmpty()) {
                compound.addChildShape(BoxCollisionShape(com.jme3.math.Vector3f(1f, 1f, 1f)), com.jme3.math.Vector3f(0f, 0f, 0f))
            }

            val mass = if (rb.bodyType == com.pafoid.skate.engine.physics3d.enums.BodyType.Static) 0f else rb.mass
            val body = PhysicsRigidBody(compound, mass)
            body.setFriction(rb.friction)
            val trans = go.transform.translation
            body.setPhysicsLocation(com.jme3.math.Vector3f(trans.x, trans.y, trans.z))
            
            physicsSpace.add(body)
            rb.rawBody = body
        }
    }

    fun remove(go: GameObject) {
        val rb = go.getComponent<com.pafoid.skate.engine.physics3d.components.RigidBody3D>()
        rb?.rawBody?.let {
            physicsSpace.remove(it)
            rb.rawBody = null
        }
    }

    fun update(dt: Float) {
        physicsTime += dt
        while (physicsTime >= 0f) {
            physicsTime -= physicsTimeStep
            physicsSpace.update(physicsTimeStep)
        }
    }

    fun destroy() {
        physicsSpace.destroy()
    }
}