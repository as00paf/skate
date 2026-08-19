package com.pafoid.skate.engine.ecs.components

import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.toQuaternion
import com.pafoid.skate.engine.physics3d.toQuaternionf
import com.pafoid.skate.engine.physics3d.toVector3f
import com.pafoid.skate.engine.utils.JmeVector3f
import com.pafoid.skate.engine.utils.JomlVector3f
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Quaternionf
import org.joml.Vector3f

@Serializable
open class RigidBody3D(
    var mass: Float = 1.0f,
    var bodyType: BodyType = BodyType.Dynamic,
    var useCCD: Boolean = false
) : Component(), IPhysicsBody3D {// TODO: cleanup

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

    @Transient
    var rawBody: PhysicsRigidBody? = null

    @Transient
    private val position: JmeVector3f = JmeVector3f()
    @Transient
    private val rotation: Quaternion = Quaternion()

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

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        rawBody?.setAngularFactor(JmeVector3f(0f, 1f, 0f))
    }

    override fun reset() {
        super.reset()
        val transform = gameObject?.getComponent<Transform>()
        val initialTranslation = transform?.translation?.let { JmeVector3f(it.x, it.y, it.z) } ?: JmeVector3f()
        val initialRotation = transform?.rotation?.let { Quaternion(it.x, it.y, it.z, 1f) } ?: Quaternion()
        position.set(initialTranslation)
        rotation.set(initialRotation)
        linearDamping = 0f
        angularDamping = 0f
        linearVelocity.set(JomlVector3f())
        angularVelocity.set(JomlVector3f())

        rawBody?.setAngularFactor(JmeVector3f(0f, 1f, 0f))
        rawBody?.setPhysicsLocation(position)
        rawBody?.setPhysicsRotation(rotation)
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
        val transform = gameObject?.getComponent<Transform>() ?: return JomlVector3f()
        val relPos = JomlVector3f(worldPos).sub(transform.translation)
        val vAtPoint = JomlVector3f(angularVelocity).cross(relPos).add(linearVelocity)
        return vAtPoint
    }

    override fun getRotation(): Quaternionf {
        rawBody?.getPhysicsRotation(rotation)
        return rotation.toQuaternionf()
    }

    override fun setRotation(rotation: Quaternionf) {
        rawBody?.setPhysicsRotation(rotation.toQuaternion())
    }

    override fun getWorldPosition(): Vector3f {
        rawBody?.getPhysicsLocation(position)
        return position.toVector3f()
    }

}
