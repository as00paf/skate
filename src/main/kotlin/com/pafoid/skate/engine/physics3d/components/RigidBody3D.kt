package com.pafoid.skate.engine.physics3d.components

import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.physics3d.enums.BodyType
import com.pafoid.skate.engine.scenes.SceneManager
import com.jme3.math.Vector3f
import com.jme3.math.Quaternion

class RigidBody3D(var mass: Float = 1.0f) : Component() {
    var bodyType: BodyType = BodyType.Dynamic
    var friction: Float = 0.5f
    var useCCD: Boolean = false
    @Transient var rawBody: PhysicsRigidBody? = null

    override fun update(dt: Float) {
        rawBody?.let { body ->
            if (SceneManager.isPlaying()) {
                val pos = body.getPhysicsLocation(null)
                val rot = body.getPhysicsRotation(null)
                
                gameObject.transform.translation.set(pos.x, pos.y, pos.z)
                
                // JME Quaternion to Euler (JOML)
                val q = org.joml.Quaternionf(rot.x, rot.y, rot.z, rot.w)
                val euler = org.joml.Vector3f()
                q.getEulerAnglesXYZ(euler)
                gameObject.transform.rotation.set(
                    Math.toDegrees(euler.x.toDouble()).toFloat(),
                    Math.toDegrees(euler.y.toDouble()).toFloat(),
                    Math.toDegrees(euler.z.toDouble()).toFloat()
                )
            } else {
                // In editor, update physics body from transform if it's changed via UI
                val trans = gameObject.transform.translation
                val rot = gameObject.transform.rotation
                
                body.setPhysicsLocation(Vector3f(trans.x, trans.y, trans.z))
                
                val q = org.joml.Quaternionf().rotationXYZ(
                    Math.toRadians(rot.x.toDouble()).toFloat(),
                    Math.toRadians(rot.y.toDouble()).toFloat(),
                    Math.toRadians(rot.z.toDouble()).toFloat()
                )
                body.setPhysicsRotation(com.jme3.math.Quaternion(q.x, q.y, q.z, q.w))
                
                body.setLinearVelocity(Vector3f.ZERO)
                body.setAngularVelocity(Vector3f.ZERO)
            }
        }
    }

    override fun editorUpdate(dt: Float) {
        update(dt)
    }

    fun applyCentralForce(force: org.joml.Vector3f) {
        rawBody?.applyCentralForce(Vector3f(force.x, force.y, force.z))
    }

    fun applyImpulse(impulse: org.joml.Vector3f) {
        rawBody?.applyImpulse(Vector3f(impulse.x, impulse.y, impulse.z), Vector3f.ZERO)
    }

    var linearVelocity: org.joml.Vector3f
        get() {
            val v = rawBody?.getLinearVelocity(null) ?: Vector3f.ZERO
            return org.joml.Vector3f(v.x, v.y, v.z)
        }
        set(value) {
            rawBody?.setLinearVelocity(Vector3f(value.x, value.y, value.z))
        }

    var angularVelocity: org.joml.Vector3f
        get() {
            val v = rawBody?.getAngularVelocity(null) ?: Vector3f.ZERO
            return org.joml.Vector3f(v.x, v.y, v.z)
        }
        set(value) {
            rawBody?.setAngularVelocity(Vector3f(value.x, value.y, value.z))
        }
}