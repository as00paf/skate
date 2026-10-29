package com.pafoid.skate.engine.physics3d.simple

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Collider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.ALL_LAYERS
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.engine.physics3d.PhysicsContactListener
import com.pafoid.skate.engine.physics3d.PhysicsHit
import com.pafoid.skate.engine.physics3d.constraints.IPhysicsConstraint
import com.pafoid.skate.engine.utils.toRadiansF
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.sqrt

/**
 * PHY-05 spike: a dependency-free second [IPhysics3D] backend. Every body is approximated by its bounding sphere;
 * it integrates gravity and damping, resolves sphere-sphere contacts (restitution, layer masks), reports collision and
 * trigger events, and answers ray queries. Joints, friction, rotation dynamics and CCD are not supported.
 * It exists to prove the abstraction is backend-agnostic, not for production use.
 */
class SimplePhysics3D : IPhysics3D {
    override var debugEnabled = false
    override var contactListener: PhysicsContactListener? = null

    private var gravity = Vector3f(0f, -9.81f, 0f)
    private val bodies = ArrayList<SimpleBody>()
    private val touching = HashSet<Pair<SimpleBody, SimpleBody>>()

    override fun getGravity(): Vector3f = Vector3f(gravity)
    override fun setGravity(gravity: Vector3f) {
        this.gravity = Vector3f(gravity)
    }

    override fun add(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>() ?: return
        if (rb.rawBody is SimpleBody) return

        val colliders = go.components.filterIsInstance<Collider3D>()
        val scale = go.getComponent<Transform>()?.scale ?: Vector3f(1f)
        val radius = if (colliders.isEmpty()) 1f else colliders.maxOf { boundingRadius(it.toShape()) }
        val body = SimpleBody(
            mass = if (rb.bodyType == BodyType.Static) 0f else rb.mass,
            radius = radius * max(scale.x, max(scale.y, scale.z)),
        )
        body.isKinematic = rb.bodyType == BodyType.Kinematic
        body.restitution = rb.restitution
        body.friction = rb.friction
        body.collisionLayer = rb.collisionLayer
        body.collisionMask = rb.collisionMask
        body.isTrigger = rb.isTrigger
        body.setDamping(rb.linearDamping, rb.angularDamping)
        body.owner = go
        go.getComponent<Transform>()?.let { t ->
            body.setLocation(t.translation)
            body.setRotation(
                Quaternionf().rotationXYZ(t.rotation.x.toRadiansF(), t.rotation.y.toRadiansF(), t.rotation.z.toRadiansF())
            )
        }
        rb.rawBody = body
        bodies.add(body)
    }

    override fun remove(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>() ?: return
        val body = rb.rawBody as? SimpleBody ?: return
        bodies.remove(body)
        touching.removeAll { it.first === body || it.second === body }
        rb.rawBody = null
    }

    override fun createStaticAnchor(location: Vector3f): IRigidBodyHandle =
        SimpleBody(0f, 0.01f).also { it.setLocation(location); bodies.add(it) }

    override fun createRigidBody(shape: ColliderShape, mass: Float): IRigidBodyHandle =
        SimpleBody(mass, boundingRadius(shape)).also { bodies.add(it) }

    override fun addConstraint(constraint: IPhysicsConstraint) =
        throw UnsupportedOperationException("SimplePhysics3D does not support joints")

    override fun removeConstraint(constraint: IPhysicsConstraint) {}

    override fun step(fixedDt: Float) {
        for (b in bodies) b.integrate(gravity, fixedDt)
        val current = HashSet<Pair<SimpleBody, SimpleBody>>()
        for (i in 0 until bodies.size) for (j in i + 1 until bodies.size) {
            val a = bodies[i]
            val b = bodies[j]
            if (a.isStatic && b.isStatic) continue
            if (a.collisionMask and b.collisionLayer == 0 || b.collisionMask and a.collisionLayer == 0) continue
            val normal = Vector3f(b.position).sub(a.position)
            val dist = normal.length()
            val overlap = a.radius + b.radius - dist
            if (overlap <= 0f) continue
            if (dist > 1e-6f) normal.div(dist) else normal.set(0f, 1f, 0f)
            val pair = a to b
            current.add(pair)
            val isNew = pair !in touching
            if (a.isTrigger || b.isTrigger) {
                if (isNew) notifyTrigger(a, b, enter = true)
            } else {
                val impulse = resolve(a, b, normal, overlap)
                val ga = a.owner
                val gb = b.owner
                if (isNew && ga != null && gb != null) {
                    contactListener?.onCollisionStarted(ga, gb, Vector3f(a.position).fma(a.radius, normal), normal, impulse)
                }
            }
        }
        for (pair in touching) {
            if (pair !in current && (pair.first.isTrigger || pair.second.isTrigger)) {
                notifyTrigger(pair.first, pair.second, enter = false)
            }
        }
        touching.clear()
        touching.addAll(current)
    }

    private fun notifyTrigger(a: SimpleBody, b: SimpleBody, enter: Boolean) {
        val trigger = if (a.isTrigger) a else b
        val other = if (trigger === a) b else a
        val gt = trigger.owner ?: return
        val go = other.owner ?: return
        if (enter) contactListener?.onTriggerEnter(gt, go) else contactListener?.onTriggerExit(gt, go)
    }

    /** Separates the pair by inverse-mass share and applies a restitution impulse along [normal] (a to b). */
    private fun resolve(a: SimpleBody, b: SimpleBody, normal: Vector3f, overlap: Float): Float {
        val invA = a.inverseMass
        val invB = b.inverseMass
        val invSum = invA + invB
        if (invSum == 0f) return 0f
        a.position.fma(-overlap * invA / invSum, normal)
        b.position.fma(overlap * invB / invSum, normal)
        val relative = Vector3f(b.linearVelocity).sub(a.linearVelocity).dot(normal)
        if (relative >= 0f) return 0f
        val j = -(1f + minOf(a.restitution, b.restitution)) * relative / invSum
        a.addVelocity(-j * invA, normal)
        b.addVelocity(j * invB, normal)
        return j
    }

    override fun raycast(from: Vector3f, to: Vector3f, layerMask: Int, excludeBody: IPhysicsBody3D?): PhysicsHit? =
        sweepSphere(0f, from, to, layerMask, excludeBody).minByOrNull { it.fraction }

    override fun raycastAll(from: Vector3f, to: Vector3f, layerMask: Int, excludeBody: IPhysicsBody3D?): List<PhysicsHit> =
        sweepSphere(0f, from, to, layerMask, excludeBody).sortedBy { it.fraction }

    /** Approximation: sweeps a bounding sphere of [shape] against body spheres. */
    override fun shapeCast(shape: ColliderShape, from: Vector3f, to: Vector3f, layerMask: Int, excludeBody: IPhysicsBody3D?): PhysicsHit? =
        sweepSphere(boundingRadius(shape), from, to, layerMask, excludeBody).minByOrNull { it.fraction }

    private fun sweepSphere(sweep: Float, from: Vector3f, to: Vector3f, layerMask: Int, excludeBody: IPhysicsBody3D?): List<PhysicsHit> {
        val excluded = (excludeBody as? RigidBody3D)?.rawBody
        val dir = Vector3f(to).sub(from)
        val length = dir.length()
        if (length < 1e-9f) return emptyList()
        dir.div(length)
        val hits = ArrayList<PhysicsHit>()
        for (b in bodies) {
            if (b === excluded || b.isTrigger || b.collisionLayer and layerMask == 0) continue
            val oc = Vector3f(from).sub(b.position)
            val r = b.radius + sweep
            val half = oc.dot(dir)
            val disc = half * half - (oc.lengthSquared() - r * r)
            if (disc < 0f) continue
            val t = -half - sqrt(disc)
            if (t < 0f || t > length) continue
            val point = Vector3f(from).fma(t, dir)
            hits.add(PhysicsHit(b.owner, point, Vector3f(point).sub(b.position).normalize(), t / length))
        }
        return hits
    }

    override fun debugDraw(dt: Float) {}

    override fun destroy() {
        bodies.clear()
        touching.clear()
    }

    private fun boundingRadius(shape: ColliderShape): Float = when (shape) {
        is ColliderShape.Box -> shape.halfExtents.length()
        is ColliderShape.Capsule -> shape.radius + shape.height / 2f
        is ColliderShape.Cylinder -> sqrt(shape.radius * shape.radius + shape.height * shape.height / 4f)
        is ColliderShape.ConvexHull -> shape.points.maxOfOrNull { it.length() } ?: 0.5f
    }
}

internal class SimpleBody(override val mass: Float, val radius: Float) : IRigidBodyHandle {
    var owner: GameObject? = null
    val position = Vector3f()
    private val rotation = Quaternionf()
    private val force = Vector3f()
    private val linear = Vector3f()
    private val angular = Vector3f()

    override var linearDamping = 0f
        private set
    override var angularDamping = 0f
        private set
    override var friction = 0.5f
    override var restitution = 0f
    override var collisionLayer = 1
    override var collisionMask = ALL_LAYERS
    override var isTrigger = false
    override var isKinematic = false

    val isStatic get() = mass == 0f && !isKinematic
    val inverseMass get() = if (mass == 0f || isKinematic) 0f else 1f / mass

    override var linearVelocity: Vector3f
        get() = Vector3f(linear)
        set(value) {
            linear.set(value)
        }
    override var angularVelocity: Vector3f
        get() = Vector3f(angular)
        set(value) {
            angular.set(value)
        }

    fun addVelocity(scale: Float, direction: Vector3f) {
        linear.fma(scale, direction)
    }

    override fun setContinuousCollision(motionThreshold: Float, sweptRadius: Float) {}
    override fun setDamping(linear: Float, angular: Float) {
        linearDamping = linear
        angularDamping = angular
    }

    override fun setAngularFactor(factor: Vector3f) {}
    override fun applyCentralForce(force: Vector3f) {
        this.force.add(force)
    }

    override fun applyImpulse(impulse: Vector3f, relPos: Vector3f) {
        linear.fma(inverseMass, impulse)
    }

    override fun applyTorqueImpulse(torque: Vector3f) {
        angular.fma(inverseMass, torque)
    }

    override fun applyForce(force: Vector3f, relPos: Vector3f) = applyCentralForce(force)
    override fun getLocation(out: Vector3f): Vector3f = out.set(position)
    override fun setLocation(location: Vector3f) {
        position.set(location)
    }

    override fun getRotation(out: Quaternionf): Quaternionf = out.set(rotation)
    override fun setRotation(rotation: Quaternionf) {
        this.rotation.set(rotation)
    }

    fun integrate(gravity: Vector3f, dt: Float) {
        if (inverseMass == 0f) {
            if (isKinematic) position.fma(dt, linear)
            return
        }
        linear.fma(dt, gravity).fma(dt * inverseMass, force)
        linear.mul(max(0f, 1f - linearDamping * dt))
        angular.mul(max(0f, 1f - angularDamping * dt))
        position.fma(dt, linear)
        if (angular.lengthSquared() > 0f) {
            rotation.rotateXYZ(angular.x * dt, angular.y * dt, angular.z * dt).normalize()
        }
        force.zero()
    }
}
