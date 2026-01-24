package com.pafoid.skate.engine.physics3d.components

import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.physics3d.enums.BodyType
import com.jme3.math.Vector3f
import com.jme3.math.Quaternion

class RigidBody3D(val mass: Float = 1.0f) : Component() {
    var bodyType: BodyType = BodyType.Dynamic
    @Transient var rawBody: PhysicsRigidBody? = null

    override fun update(dt: Float) {
        rawBody?.let { body ->
            val pos = body.getPhysicsLocation(null)
            val rot = body.getPhysicsRotation(null)
            
            gameObject.transform.translation.set(pos.x, pos.y, pos.z)
            
            // Convert JME Quaternion to JOML/Euler if needed, 
            // but for now we'll stick to translation for simplicity in the first pass
            // TODO: Proper rotation sync
        }
    }

    fun applyCentralForce(force: org.joml.Vector3f) {
        rawBody?.applyCentralForce(Vector3f(force.x, force.y, force.z))
    }

    fun applyImpulse(impulse: org.joml.Vector3f) {
        rawBody?.applyImpulse(Vector3f(impulse.x, impulse.y, impulse.z), Vector3f.ZERO)
    }
}