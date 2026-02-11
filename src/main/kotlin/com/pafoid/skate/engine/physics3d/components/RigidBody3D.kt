package com.pafoid.skate.engine.physics3d.components

import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.utils.JmeVector3f
import com.pafoid.skate.engine.utils.JomlVector3f
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Quaternionf

@Serializable
open class RigidBody3D(var mass: Float = 1.0f) : Component(), IPhysicsBody3D {
    
    var bodyType: BodyType = BodyType.Dynamic
    var useCCD: Boolean = false
    
    var friction: Float = 0.5f
        set(value) {
            field = value
            rawBody?.friction = value
        }
        
    var linearDamping: Float = 0.0f
        set(value) {
            field = value
            rawBody?.setDamping(value, angularDamping)
        }
        
    var angularDamping: Float = 0.0f
        set(value) {
            field = value
            rawBody?.setDamping(linearDamping, value)
        }

    @Transient var rawBody: PhysicsRigidBody? = null

    override fun update(dt: Float) {
        rawBody?.let { body ->
            val transform = gameObject.getComponent<Transform>() ?: return
            val pos = body.getPhysicsLocation(null)
            val rot = body.getPhysicsRotation(null)

            transform.translation.set(pos.x, pos.y, pos.z)

            // JME Quaternion to Euler (JOML)
            val q = Quaternionf(rot.x, rot.y, rot.z, rot.w)
            val euler = JomlVector3f()
            q.getEulerAnglesXYZ(euler)
            transform.rotation.set(
                Math.toDegrees(euler.x.toDouble()).toFloat(),
                Math.toDegrees(euler.y.toDouble()).toFloat(),
                Math.toDegrees(euler.z.toDouble()).toFloat()
            )
        }
    }

    override fun editorUpdate(dt: Float) {
        rawBody?.let { body ->
            // Zero out velocity in editor to prevent drifting while editing properties
            body.setLinearVelocity(JmeVector3f.ZERO)
            body.setAngularVelocity(JmeVector3f.ZERO)
        }
    }

    override fun applyCentralForce(force: JomlVector3f) {
        rawBody?.applyCentralForce(JmeVector3f(force.x, force.y, force.z))
    }

    override fun applyImpulse(impulse: JomlVector3f) {
        rawBody?.applyImpulse(JmeVector3f(impulse.x, impulse.y, impulse.z), JmeVector3f.ZERO)
    }

    override fun applyTorqueImpulse(torque: JomlVector3f) {
        rawBody?.applyTorqueImpulse(JmeVector3f(torque.x, torque.y, torque.z))
    }

    override fun applyForce(force: JomlVector3f, relPos: JomlVector3f) {
        rawBody?.applyForce(JmeVector3f(force.x, force.y, force.z), JmeVector3f(relPos.x, relPos.y, relPos.z))
    }

    override fun getVelocityInPoint(worldPos: JomlVector3f): JomlVector3f {
        val transform = gameObject.getComponent<Transform>() ?: return JomlVector3f()
        val relPos = JomlVector3f(worldPos).sub(transform.translation)
        val vAtPoint = JomlVector3f(angularVelocity).cross(relPos).add(linearVelocity)
        return vAtPoint
    }

    override var linearVelocity: JomlVector3f
        get() {
            val v: JmeVector3f = rawBody?.getLinearVelocity(null) ?: JmeVector3f.ZERO
            return JomlVector3f(v.x, v.y, v.z)
        }
        set(value) {
            rawBody?.setLinearVelocity(JmeVector3f(value.x, value.y, value.z))
        }

    override var angularVelocity: JomlVector3f
        get() {
            val v = rawBody?.getAngularVelocity(null) ?: JmeVector3f.ZERO
            return JomlVector3f(v.x, v.y, v.z)
        }
        set(value) {
            rawBody?.setAngularVelocity(JmeVector3f(value.x, value.y, value.z))
        }
}
