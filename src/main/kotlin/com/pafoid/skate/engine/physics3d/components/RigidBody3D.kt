package com.pafoid.skate.engine.physics3d.components

import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
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

/**
 * Represents a physics rigid body component that can be attached to a GameObject.
 * It manages the physical properties (mass, friction, damping) and synchronizes
 * with the underlying Bullet Physics engine.
 *
 * @property mass The mass of the rigid body in kg. 0.0f makes it static.
 */
@Serializable
open class RigidBody3D(var mass: Float = 1.0f) : Component(), IPhysicsBody3D {
    
    /**
     * The type of physics body (Dynamic, Static, Kinematic).
     */
    var bodyType: BodyType = BodyType.Dynamic

    /**
     * Whether to use Continuous Collision Detection (CCD) for fast moving objects.
     */
    var useCCD: Boolean = false
    
    /**
     * The friction coefficient of the surface.
     * Updates the underlying physics body immediately.
     */
    var friction: Float = 0.5f
        set(value) {
            field = value
            rawBody?.friction = value
        }
        
    /**
     * The linear damping (air resistance) applied to linear velocity.
     * Updates the underlying physics body immediately.
     */
    var linearDamping: Float = 0.0f
        set(value) {
            field = value
            rawBody?.setDamping(value, angularDamping)
        }
        
    /**
     * The angular damping (air resistance) applied to angular velocity.
     * Updates the underlying physics body immediately.
     */
    var angularDamping: Float = 0.0f
        set(value) {
            field = value
            rawBody?.setDamping(linearDamping, value)
        }

    override fun start() {
        rawBody?.setAngularFactor(JmeVector3f(0f, 1f, 0f))
    }

    /**
     * The raw JBullet PhysicsRigidBody instance.
     * Null if not yet added to the physics world.
     */
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

    /**
     * Applies a force to the center of mass of the rigid body.
     *
     * @param force The force vector to apply.
     */
    override fun applyCentralForce(force: JomlVector3f) {
        rawBody?.applyCentralForce(JmeVector3f(force.x, force.y, force.z))
    }

    /**
     * Applies an instantaneous impulse to the rigid body.
     * Useful for sudden impacts like jumps or explosions.
     *
     * @param impulse The impulse vector.
     */
    override fun applyImpulse(impulse: JomlVector3f) {
        rawBody?.applyImpulse(JmeVector3f(impulse.x, impulse.y, impulse.z), JmeVector3f.ZERO)
    }

    /**
     * Applies an instantaneous torque impulse to the rigid body.
     * Useful for sudden rotations.
     *
     * @param torque The torque impulse vector.
     */
    override fun applyTorqueImpulse(torque: JomlVector3f) {
        rawBody?.applyTorqueImpulse(JmeVector3f(torque.x, torque.y, torque.z))
    }

    /**
     * Applies a force at a specific position relative to the center of mass.
     * This will produce both linear and angular acceleration.
     *
     * @param force The force vector.
     * @param relPos The position relative to the center of mass where the force is applied.
     */
    override fun applyForce(force: JomlVector3f, relPos: JomlVector3f) {
        rawBody?.applyForce(JmeVector3f(force.x, force.y, force.z), JmeVector3f(relPos.x, relPos.y, relPos.z))
    }

    /**
     * Calculates the total velocity (linear + angular) at a specific point in world space.
     *
     * @param worldPos The point in world space.
     * @return The velocity vector at that point.
     */
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

    @Transient
    private val rotation: Quaternion = Quaternion()

    override fun getRotation(): Quaternionf {
        rawBody?.getPhysicsRotation(rotation)
        return rotation.toQuaternionf()
    }

    override fun setRotation(rotation: Quaternionf) {
        rawBody?.setPhysicsRotation(rotation.toQuaternion())
    }

    @Transient
    private val position: JmeVector3f = JmeVector3f()

    override fun getWorldPosition(): Vector3f {
        rawBody?.getPhysicsLocation(position)
        return position.toVector3f()
    }
}
