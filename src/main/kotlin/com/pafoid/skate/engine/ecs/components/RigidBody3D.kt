package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.ALL_LAYERS
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.engine.utils.JomlVector3f
import com.pafoid.skate.engine.utils.toRadiansF
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

    /** Bounciness in [0, 1]; 0 absorbs all impact energy. */
    var restitution: Float = 0.0f
        set(value) {
            field = value
            rawBody?.restitution = value
        }

    /** Layer bit flags this body belongs to. */
    var collisionLayer: Int = 1
        set(value) {
            field = value
            rawBody?.collisionLayer = value
        }

    /** Layer bit flags this body collides with; [ALL_LAYERS] collides with everything. */
    var collisionMask: Int = ALL_LAYERS
        set(value) {
            field = value
            rawBody?.collisionMask = value
        }

    /** Triggers report overlaps through trigger events and never push other bodies. */
    var isTrigger: Boolean = false
        set(value) {
            field = value
            rawBody?.isTrigger = value
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
    var rawBody: IRigidBodyHandle? = null

    @Transient
    private val position: Vector3f = Vector3f()
    @Transient
    private val rotation: Quaternionf = Quaternionf()
    @Transient
    private val origin: Vector3f = Vector3f()

    override var linearVelocity: JomlVector3f
        get() = rawBody?.linearVelocity ?: JomlVector3f()
        set(value) {
            rawBody?.linearVelocity = value
        }

    override var angularVelocity: JomlVector3f
        get() = rawBody?.angularVelocity ?: JomlVector3f()
        set(value) {
            rawBody?.angularVelocity = value
        }

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        rawBody?.setAngularFactor(Vector3f(0f, 1f, 0f))
    }

    override fun reset() {
        super.reset()
        val transform = gameObject?.getComponent<Transform>()
        val initialTranslation = transform?.translation?.let { Vector3f(it) } ?: Vector3f()
        val initialRotation = transform?.rotation?.let { Quaternionf().rotationXYZ(it.x.toRadiansF(), it.y.toRadiansF(), it.z.toRadiansF()) } ?: Quaternionf()
        position.set(initialTranslation)
        rotation.set(initialRotation)
        linearDamping = 0f
        angularDamping = 0f
        linearVelocity = JomlVector3f()
        angularVelocity = JomlVector3f()

        rawBody?.setAngularFactor(Vector3f(0f, 1f, 0f))
        rawBody?.setLocation(position)
        rawBody?.setRotation(rotation)
    }

    override fun applyCentralForce(force: JomlVector3f) {
        rawBody?.applyCentralForce(force)
    }

    override fun applyImpulse(impulse: JomlVector3f) {
        rawBody?.applyImpulse(impulse, origin)
    }

    override fun applyTorqueImpulse(torque: JomlVector3f) {
        rawBody?.applyTorqueImpulse(torque)
    }

    override fun applyForce(force: JomlVector3f, relPos: JomlVector3f) {
        rawBody?.applyForce(force, relPos)
    }

    override fun getVelocityInPoint(worldPos: JomlVector3f): JomlVector3f {
        val transform = gameObject?.getComponent<Transform>() ?: return JomlVector3f()
        val relPos = JomlVector3f(worldPos).sub(transform.translation)
        val vAtPoint = JomlVector3f(angularVelocity).cross(relPos).add(linearVelocity)
        return vAtPoint
    }

    override fun getRotation(): Quaternionf {
        rawBody?.getRotation(rotation)
        return Quaternionf(rotation)
    }

    override fun setRotation(rotation: Quaternionf) {
        rawBody?.setRotation(rotation)
    }

    override fun getWorldPosition(): Vector3f {
        rawBody?.getLocation(position)
        return Vector3f(position)
    }

}
