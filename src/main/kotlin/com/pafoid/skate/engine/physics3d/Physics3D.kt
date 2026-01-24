package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.shapes.BoxCollisionShape
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

    fun add(go: GameObject) {
        val rb = go.getComponent<com.pafoid.skate.engine.physics3d.components.RigidBody3D>()
        if (rb != null && rb.rawBody == null) {
            val collider = go.getComponent<com.pafoid.skate.engine.physics3d.components.BoxCollider3D>()
            val shape = if (collider != null) {
                BoxCollisionShape(com.jme3.math.Vector3f(collider.halfExtents.x, collider.halfExtents.y, collider.halfExtents.z))
            } else {
                BoxCollisionShape(com.jme3.math.Vector3f(1f, 1f, 1f))
            }

            val body = PhysicsRigidBody(shape, rb.mass)
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