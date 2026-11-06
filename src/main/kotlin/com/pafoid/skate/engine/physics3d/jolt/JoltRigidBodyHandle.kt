package com.pafoid.skate.engine.physics3d.jolt

import com.github.stephengold.joltjni.Body
import com.github.stephengold.joltjni.BodyInterface
import com.github.stephengold.joltjni.Quat
import com.github.stephengold.joltjni.RVec3
import com.github.stephengold.joltjni.enumerate.EActivation
import com.github.stephengold.joltjni.enumerate.EMotionQuality
import com.github.stephengold.joltjni.enumerate.EMotionType
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * [IRigidBodyHandle] over one Jolt body. Positions are the body **origin** (not its centre of mass), so Transform sync
 * matches Bullet; impulse and force offsets are relative to the centre of mass, as in Bullet. Only touched from the
 * thread that steps the world.
 */
internal class JoltRigidBodyHandle(
    private val bodies: BodyInterface,
    private val layers: JoltLayers,
    val body: Body,
    val motionType: BodyType,
    override val mass: Float,
    layer: Int,
    mask: Int,
    trigger: Boolean,
    linearDamping: Float,
    angularDamping: Float,
    /** Owning GameObject, or null for standalone bodies (ragdoll bones, joint anchors). */
    val gameObject: GameObject?,
) : IRigidBodyHandle {
    val id: Int = body.id

    private val tempR = RVec3()
    private val tempQ = Quat()
    private var allowedDofs = JoltConversions.ALL_DOFS

    override var linearDamping: Float = linearDamping
        private set
    override var angularDamping: Float = angularDamping
        private set

    override var friction: Float
        get() = bodies.getFriction(id)
        set(value) = bodies.setFriction(id, value)

    override var restitution: Float
        get() = bodies.getRestitution(id)
        set(value) = bodies.setRestitution(id, value)

    override var collisionLayer: Int = layer
        set(value) {
            field = value
            updateObjectLayer()
        }

    override var collisionMask: Int = mask
        set(value) {
            field = value
            updateObjectLayer()
        }

    /** Whether the body was kinematic before it became a trigger (triggers are held kinematic, as in Bullet). */
    private var kinematicBeforeTrigger = motionType == BodyType.Kinematic

    /** Triggers are Jolt sensors: they report overlaps and never push other bodies. A moving trigger is held kinematic. */
    override var isTrigger: Boolean = trigger
        set(value) {
            if (value == field) return
            field = value
            bodies.setIsSensor(id, value)
            if (!body.isStatic) {
                if (value) kinematicBeforeTrigger = body.isKinematic
                isKinematic = value || kinematicBeforeTrigger
            }
            wake()
        }

    override var isKinematic: Boolean
        get() = body.isKinematic
        set(value) {
            if (body.isStatic) return
            bodies.setMotionType(id, if (value) EMotionType.Kinematic else EMotionType.Dynamic, EActivation.Activate)
        }

    override var linearVelocity: Vector3f
        get() = body.getLinearVelocity().let { Vector3f(it.x, it.y, it.z) }
        set(value) {
            if (!body.isStatic) bodies.setLinearVelocity(id, value.x, value.y, value.z)
        }

    override var angularVelocity: Vector3f
        get() = body.getAngularVelocity().let { Vector3f(it.x, it.y, it.z) }
        set(value) {
            if (!body.isStatic) bodies.setAngularVelocity(id, value.x, value.y, value.z)
        }

    /** Jolt sizes the swept cast itself, so the threshold and radius are not used. */
    override fun setContinuousCollision(motionThreshold: Float, sweptRadius: Float) {
        if (!body.isStatic) bodies.setMotionQuality(id, EMotionQuality.LinearCast)
    }

    override fun setDamping(linear: Float, angular: Float) {
        linearDamping = linear
        angularDamping = angular
        if (body.isStatic) return
        val motion = body.motionProperties
        motion.setLinearDamping(JoltConversions.damping(linear))
        motion.setAngularDamping(JoltConversions.damping(angular))
    }

    /** A zero component locks rotation about that axis; Jolt has no partial factors, so non-zero means free. */
    override fun setAngularFactor(factor: Vector3f) {
        if (body.isStatic) return
        val dofs = JoltConversions.allowedDofs(factor)
        if (dofs == allowedDofs) return
        allowedDofs = dofs
        val massProperties = body.shape.massProperties
        massProperties.scaleToMass(if (mass > 0f) mass else 1f)
        body.motionProperties.setMassProperties(dofs, massProperties)
        massProperties.close()
    }

    override fun applyCentralForce(force: Vector3f) = bodies.addForce(id, force.x, force.y, force.z)

    override fun applyImpulse(impulse: Vector3f, relPos: Vector3f) {
        body.getCenterOfMassPosition(tempR)
        bodies.addImpulse(id, impulse.x, impulse.y, impulse.z, tempR.xx() + relPos.x, tempR.yy() + relPos.y, tempR.zz() + relPos.z)
    }

    override fun applyTorqueImpulse(torque: Vector3f) = bodies.addAngularImpulse(id, torque.x, torque.y, torque.z)

    override fun applyForce(force: Vector3f, relPos: Vector3f) {
        body.getCenterOfMassPosition(tempR)
        bodies.addForce(id, force.x, force.y, force.z, tempR.xx() + relPos.x, tempR.yy() + relPos.y, tempR.zz() + relPos.z)
    }

    override fun getLocation(out: Vector3f): Vector3f {
        body.getPosition(tempR)
        return out.set(tempR.x(), tempR.y(), tempR.z())
    }

    override fun setLocation(location: Vector3f) =
        bodies.setPosition(id, location.x.toDouble(), location.y.toDouble(), location.z.toDouble(), EActivation.Activate)

    override fun getRotation(out: Quaternionf): Quaternionf {
        body.getRotation(tempQ)
        return out.set(tempQ.x, tempQ.y, tempQ.z, tempQ.w)
    }

    override fun setRotation(rotation: Quaternionf) {
        body.getPosition(tempR)
        tempQ.set(rotation.x, rotation.y, rotation.z, rotation.w)
        bodies.setPositionAndRotation(id, tempR, tempQ, EActivation.Activate)
    }

    /** Moves the body to [position]/[rotation]; with [clearMotion] also clears its velocities and accumulated forces. */
    internal fun setPose(position: Vector3f, rotation: Quaternionf, clearMotion: Boolean) {
        tempR.set(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
        tempQ.set(rotation.x, rotation.y, rotation.z, rotation.w)
        bodies.setPositionAndRotation(id, tempR, tempQ, EActivation.Activate)
        if (!clearMotion || body.isStatic) return
        bodies.setLinearAndAngularVelocity(id, 0f, 0f, 0f, 0f, 0f, 0f)
        val motion = body.motionProperties
        motion.resetForce()
        motion.resetTorque()
    }

    /** A sleeping body would keep ignoring a filter change until something else woke it. */
    private fun updateObjectLayer() {
        bodies.setObjectLayer(id, layers.objectLayer(collisionLayer, collisionMask, motionType != BodyType.Static))
        wake()
    }

    private fun wake() {
        if (!body.isStatic) bodies.activateBody(id)
    }
}
