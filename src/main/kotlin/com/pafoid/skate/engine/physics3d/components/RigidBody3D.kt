package com.pafoid.skate.engine.physics3d.components

import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
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
            val pos = body.getPhysicsLocation(null)
            val rot = body.getPhysicsRotation(null)

            gameObject.transform.translation.set(pos.x, pos.y, pos.z)

            // JME Quaternion to Euler (JOML)
            val q = Quaternionf(rot.x, rot.y, rot.z, rot.w)
            val euler = JomlVector3f()
            q.getEulerAnglesXYZ(euler)
            gameObject.transform.rotation.set(
                Math.toDegrees(euler.x.toDouble()).toFloat(),
                Math.toDegrees(euler.y.toDouble()).toFloat(),
                Math.toDegrees(euler.z.toDouble()).toFloat()
            )
        }
    }

    override fun editorUpdate(dt: Float) {
        rawBody?.let { body ->
            // In editor, update physics body from transform if it's changed via UI
            val trans = gameObject.transform.translation
            val rot = gameObject.transform.rotation
            val scale = gameObject.transform.scale

            body.setPhysicsLocation(JmeVector3f(trans.x, trans.y, trans.z))

            val q = Quaternionf().rotationXYZ(
                Math.toRadians(rot.x.toDouble()).toFloat(),
                Math.toRadians(rot.y.toDouble()).toFloat(),
                Math.toRadians(rot.z.toDouble()).toFloat()
            )
            body.setPhysicsRotation(com.jme3.math.Quaternion(q.x, q.y, q.z, q.w))
            body.collisionShape.setScale(com.jme3.math.Vector3f(scale.x, scale.y, scale.z))

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
