package com.pafoid.skate.engine.physics3d.bullet

import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import org.joml.Quaternionf
import org.joml.Vector3f

class BulletRigidBodyHandle(val body: PhysicsRigidBody) : IRigidBodyHandle {
    private val tempVec = JmeVector3f()
    private val tempQuat = com.jme3.math.Quaternion()

    override val mass: Float get() = body.mass
    override val linearDamping: Float get() = body.linearDamping
    override val angularDamping: Float get() = body.angularDamping
    override var friction: Float
        get() = body.friction
        set(value) { body.friction = value }
    override var isKinematic: Boolean
        get() = body.isKinematic
        set(value) { body.isKinematic = value }
    override var linearVelocity: Vector3f
        get() = body.getLinearVelocity(null).toVector3f()
        set(value) { body.setLinearVelocity(value.toJme()) }
    override var angularVelocity: Vector3f
        get() = body.getAngularVelocity(null).toVector3f()
        set(value) { body.setAngularVelocity(value.toJme()) }

    override fun setDamping(linear: Float, angular: Float) = body.setDamping(linear, angular)
    override fun setAngularFactor(factor: Vector3f) = body.setAngularFactor(factor.toJme())
    override fun applyCentralForce(force: Vector3f) = body.applyCentralForce(force.toJme())
    override fun applyImpulse(impulse: Vector3f, relPos: Vector3f) = body.applyImpulse(impulse.toJme(), relPos.toJme())
    override fun applyTorqueImpulse(torque: Vector3f) = body.applyTorqueImpulse(torque.toJme())
    override fun applyForce(force: Vector3f, relPos: Vector3f) = body.applyForce(force.toJme(), relPos.toJme())

    override fun getLocation(out: Vector3f): Vector3f {
        val l = body.getPhysicsLocation(tempVec)
        return out.set(l.x, l.y, l.z)
    }

    override fun setLocation(location: Vector3f) = body.setPhysicsLocation(location.toJme())

    override fun getRotation(out: Quaternionf): Quaternionf {
        val r = body.getPhysicsRotation(tempQuat)
        return out.set(r.x, r.y, r.z, r.w)
    }

    override fun setRotation(rotation: Quaternionf) = body.setPhysicsRotation(rotation.toQuaternion())
}
