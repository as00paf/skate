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
 * PHY-05 spike: a dependency-free second [IPhysics3D] backend. Moving bodies are approximated by their bounding sphere.
 * It integrates gravity and damping, resolves contacts (restitution, layer masks), reports collision and trigger
 * events, and answers ray queries. Joints, friction, rotation dynamics and CCD are not supported.
 *
 * PHY-25 (AST-17 box fallback): a **static** body whose only collider is a box keeps its real oriented box (position,
 * rotation, scale and collider offset) for contacts, rays and shape casts, so a city made of boxes is walkable. Static
 * bodies sit in a grid broad phase ([StaticGrid]); moving bodies test each other pairwise and the static bodies near
 * them.
 */
class SimplePhysics3D : IPhysics3D {
    override var debugEnabled = false
    override var contactListener: PhysicsContactListener? = null

    private var gravity = Vector3f(0f, -9.81f, 0f)
    private val bodies = ArrayList<SimpleBody>()
    private val touching = HashSet<Pair<SimpleBody, SimpleBody>>()
    private val grid = StaticGrid()
    private val movers = ArrayList<SimpleBody>()
    private var layoutDirty = true
    private var nextId = 0
    private val markDirty: () -> Unit = { layoutDirty = true }

    /** Narrow-phase pair tests in the last [step], for broad-phase tests. */
    internal var lastNarrowPhaseTests = 0
        private set

    override val rigidBodyCount: Int get() = bodies.size

    override fun getGravity(): Vector3f = Vector3f(gravity)
    override fun setGravity(gravity: Vector3f) {
        this.gravity = Vector3f(gravity)
    }

    override fun add(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>() ?: return
        if (rb.rawBody is SimpleBody) return

        val colliders = go.components.filterIsInstance<Collider3D>()
        val transform = go.getComponent<Transform>()
        val scale = transform?.scale ?: Vector3f(1f)
        // Like BodyDescription: unavailable colliders add nothing, and with none available there is no body
        val available = colliders.mapNotNull { c -> c.toShapeOrNull()?.let { it to c } }
        if (colliders.isNotEmpty() && available.isEmpty()) return
        val radius = if (available.isEmpty()) 1f else available.maxOf { (shape, _) -> boundingRadius(shape) }
        val isStatic = rb.bodyType == BodyType.Static || colliders.any { it.staticOnly } ||
            (rb.bodyType == BodyType.Dynamic && rb.mass <= 0f)
        val box = (available.singleOrNull()?.takeIf { isStatic })?.let { (shape, c) ->
            (shape as? ColliderShape.Box)?.let { OrientedBox(Vector3f(it.halfExtents).mul(scale), Vector3f(c.offset).mul(scale)) }
        }
        val body = SimpleBody(
            mass = if (isStatic) 0f else rb.mass,
            radius = radius * max(scale.x, max(scale.y, scale.z)),
            box = box,
        )
        body.isKinematic = rb.bodyType == BodyType.Kinematic && colliders.none { it.staticOnly }
        body.restitution = rb.restitution
        body.friction = rb.friction
        body.collisionLayer = rb.collisionLayer
        body.collisionMask = rb.collisionMask
        body.isTrigger = rb.isTrigger
        body.setDamping(rb.linearDamping, rb.angularDamping)
        body.owner = go
        transform?.let { t ->
            body.setLocation(t.translation)
            body.setRotation(rotationOf(t))
        }
        rb.rawBody = body
        register(body)
    }

    private fun register(body: SimpleBody) {
        body.id = nextId++
        body.onLayoutChange = markDirty
        bodies.add(body)
        layoutDirty = true
    }

    private fun rotationOf(t: Transform) =
        Quaternionf().rotationXYZ(t.rotation.x.toRadiansF(), t.rotation.y.toRadiansF(), t.rotation.z.toRadiansF())

    override fun teleport(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>() ?: return
        val body = rb.rawBody as? SimpleBody ?: return add(go)
        go.getComponent<Transform>()?.let { t ->
            body.setLocation(t.translation)
            body.setRotation(rotationOf(t))
        }
        body.linearVelocity = Vector3f()
        body.angularVelocity = Vector3f()
    }

    override fun remove(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>() ?: return
        val body = rb.rawBody as? SimpleBody ?: return
        unregister(body)
        rb.rawBody = null
    }

    private fun unregister(body: SimpleBody) {
        if (!bodies.remove(body)) return
        body.onLayoutChange = null
        touching.removeAll { it.first === body || it.second === body }
        layoutDirty = true
    }

    override fun createStaticAnchor(location: Vector3f): IRigidBodyHandle =
        SimpleBody(0f, 0.01f).also { it.setLocation(location); register(it) }

    override fun createRigidBody(shape: ColliderShape, mass: Float): IRigidBodyHandle {
        val box = (shape as? ColliderShape.Box)?.takeIf { mass <= 0f }?.let { OrientedBox(it.halfExtents) }
        return SimpleBody(max(mass, 0f), boundingRadius(shape), box).also { register(it) }
    }

    override fun removeRigidBody(handle: IRigidBodyHandle) {
        (handle as? SimpleBody)?.let(::unregister)
    }

    override fun addConstraint(constraint: IPhysicsConstraint) =
        throw UnsupportedOperationException("SimplePhysics3D does not support joints")

    override fun removeConstraint(constraint: IPhysicsConstraint) {}

    private fun refreshLayout() {
        if (!layoutDirty) return
        layoutDirty = false
        movers.clear()
        bodies.filterTo(movers) { !it.isStatic }
        grid.rebuild(bodies.filter { it.isStatic })
    }

    override fun step(fixedDt: Float) {
        refreshLayout()
        for (b in movers) b.integrate(gravity, fixedDt)
        val current = HashSet<Pair<SimpleBody, SimpleBody>>()
        var tests = 0
        val lo = Vector3f()
        val hi = Vector3f()
        for (i in movers.indices) {
            val a = movers[i]
            for (j in i + 1 until movers.size) {
                tests++
                collide(a, movers[j], current)
            }
            a.bounds(lo, hi)
            grid.query(lo, hi) { s ->
                tests++
                collide(a, s, current)
            }
        }
        lastNarrowPhaseTests = tests
        for (pair in touching) {
            if (pair !in current && (pair.first.isTrigger || pair.second.isTrigger)) {
                notifyTrigger(pair.first, pair.second, enter = false)
            }
        }
        touching.clear()
        touching.addAll(current)
    }

    /** Narrow phase for one pair; [first] and [second] are ordered by id so pair keys are stable across steps. */
    private fun collide(x: SimpleBody, y: SimpleBody, current: MutableSet<Pair<SimpleBody, SimpleBody>>) {
        val a = if (x.id < y.id) x else y
        val b = if (a === x) y else x
        if (a.isStatic && b.isStatic) return
        if (a.collisionMask and b.collisionLayer == 0 || b.collisionMask and a.collisionLayer == 0) return
        val contact = contact(a, b) ?: return
        val pair = a to b
        current.add(pair)
        val isNew = pair !in touching
        if (a.isTrigger || b.isTrigger) {
            if (isNew) notifyTrigger(a, b, enter = true)
            return
        }
        val impulse = resolve(a, b, contact.normal, contact.depth)
        val ga = a.owner
        val gb = b.owner
        if (isNew && ga != null && gb != null) contactListener?.onCollisionStarted(ga, gb, contact.point, contact.normal, impulse)
    }

    /** Contact between [a] and [b] with the normal pointing from a to b, or null when they do not touch. */
    private fun contact(a: SimpleBody, b: SimpleBody): BoxContact? {
        a.staticBox?.let { box -> return box.sphereContact(b.position, b.radius) }
        b.staticBox?.let { box ->
            val c = box.sphereContact(a.position, a.radius) ?: return null
            return BoxContact(c.normal.negate(), c.depth, c.point)
        }
        val normal = Vector3f(b.position).sub(a.position)
        val dist = normal.length()
        val overlap = a.radius + b.radius - dist
        if (overlap <= 0f) return null
        if (dist > 1e-6f) normal.div(dist) else normal.set(0f, 1f, 0f)
        return BoxContact(normal, overlap, Vector3f(a.position).fma(a.radius, normal))
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
        sweep(0f, from, to, layerMask, excludeBody).minByOrNull { it.fraction }

    override fun raycastAll(from: Vector3f, to: Vector3f, layerMask: Int, excludeBody: IPhysicsBody3D?): List<PhysicsHit> =
        sweep(0f, from, to, layerMask, excludeBody).sortedBy { it.fraction }

    /** Sweeps a bounding sphere of [shape]: against moving bodies' spheres and static boxes (grown by its radius). */
    override fun shapeCast(shape: ColliderShape, from: Vector3f, to: Vector3f, layerMask: Int, excludeBody: IPhysicsBody3D?): PhysicsHit? =
        sweep(boundingRadius(shape), from, to, layerMask, excludeBody).minByOrNull { it.fraction }

    private fun sweep(radius: Float, from: Vector3f, to: Vector3f, layerMask: Int, excludeBody: IPhysicsBody3D?): List<PhysicsHit> {
        refreshLayout()
        val excluded = (excludeBody as? RigidBody3D)?.rawBody
        val dir = Vector3f(to).sub(from)
        val length = dir.length()
        if (length < 1e-9f) return emptyList()
        dir.div(length)
        val hits = ArrayList<PhysicsHit>()
        fun test(b: SimpleBody) {
            if (b === excluded || b.isTrigger || b.collisionLayer and layerMask == 0) return
            sweepBody(b, radius, from, dir, length)?.let(hits::add)
        }
        movers.forEach(::test)
        val lo = Vector3f(from).min(to).sub(radius, radius, radius)
        val hi = Vector3f(from).max(to).add(radius, radius, radius)
        grid.query(lo, hi, ::test)
        return hits
    }

    private fun sweepBody(b: SimpleBody, radius: Float, from: Vector3f, dir: Vector3f, length: Float): PhysicsHit? {
        b.staticBox?.let { box ->
            val hit = box.raycast(from, dir, length, radius) ?: return null
            return PhysicsHit(b.owner, Vector3f(from).fma(hit.distance, dir), hit.normal, hit.distance / length)
        }
        val oc = Vector3f(from).sub(b.position)
        val r = b.radius + radius
        val half = oc.dot(dir)
        val disc = half * half - (oc.lengthSquared() - r * r)
        if (disc < 0f) return null
        val t = -half - sqrt(disc)
        if (t < 0f || t > length) return null
        val point = Vector3f(from).fma(t, dir)
        return PhysicsHit(b.owner, point, Vector3f(point).sub(b.position).normalize(), t / length)
    }

    override fun debugDraw(dt: Float) {}

    override fun destroy() {
        bodies.forEach { it.onLayoutChange = null }
        bodies.clear()
        touching.clear()
        layoutDirty = true
    }

    private fun boundingRadius(shape: ColliderShape): Float = when (shape) {
        is ColliderShape.Box -> shape.halfExtents.length()
        is ColliderShape.Capsule -> shape.radius + shape.height / 2f
        is ColliderShape.Cylinder -> sqrt(shape.radius * shape.radius + shape.height * shape.height / 4f)
        is ColliderShape.ConvexHull -> shape.points.maxOfOrNull { it.length() } ?: 0.5f
        is ColliderShape.CompoundHull -> shape.hulls.flatten().maxOfOrNull { it.length() } ?: 0.5f
        is ColliderShape.TriangleMesh -> shape.vertices.maxOfOrNull { it.length() } ?: 0.5f
    }
}

/**
 * A SimplePhysics3D body: a sphere of [radius], or, when static with a [box], that oriented box (PHY-25).
 * [onLayoutChange] tells the world that the static broad phase must be rebuilt.
 */
internal class SimpleBody(override val mass: Float, val radius: Float, private val box: OrientedBox? = null) : IRigidBodyHandle {
    var owner: GameObject? = null
    var id = 0
    var queryStamp = 0
    var onLayoutChange: (() -> Unit)? = null
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
        set(value) {
            if (field == value) return
            field = value
            onLayoutChange?.invoke()
        }

    val isStatic get() = mass == 0f && !isKinematic
    val inverseMass get() = if (mass == 0f || isKinematic) 0f else 1f / mass

    /** The oriented box used for contacts and queries; only while the body is static. */
    val staticBox: OrientedBox? get() = box?.takeIf { isStatic }

    /** World-space bounds for the broad phase. */
    fun bounds(min: Vector3f, max: Vector3f) {
        staticBox?.let { return it.bounds(min, max) }
        min.set(position).sub(radius, radius, radius)
        max.set(position).add(radius, radius, radius)
    }

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
        placed()
    }

    override fun getRotation(out: Quaternionf): Quaternionf = out.set(rotation)
    override fun setRotation(rotation: Quaternionf) {
        this.rotation.set(rotation)
        placed()
    }

    /** Static bodies moved by hand (editor, teleport) must be re-filed in the broad phase. */
    private fun placed() {
        box?.place(position, rotation)
        if (isStatic) onLayoutChange?.invoke()
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
