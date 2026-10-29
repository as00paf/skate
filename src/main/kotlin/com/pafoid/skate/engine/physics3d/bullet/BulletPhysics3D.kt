package com.pafoid.skate.engine.physics3d.bullet
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsCollisionListener
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.ConvexShape
import com.pafoid.skate.engine.physics3d.ALL_LAYERS
import com.pafoid.skate.engine.physics3d.PhysicsContactListener
import com.pafoid.skate.engine.physics3d.PhysicsHit
import com.jme3.bullet.joints.ConeJoint
import com.jme3.bullet.joints.HingeJoint
import com.jme3.bullet.joints.PhysicsJoint
import com.jme3.bullet.joints.SixDofJoint
import com.jme3.bullet.joints.SliderJoint
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.collision.shapes.CylinderCollisionShape
import com.jme3.bullet.collision.shapes.HullCollisionShape
import com.jme3.bullet.collision.shapes.MeshCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Matrix3f
import com.jme3.math.Quaternion
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.Collider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.engine.physics3d.constraints.ConeTwistConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.FixedConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.HingeConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.SixDofConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.SliderConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.IPhysicsConstraint
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.JomlVector3f
import com.pafoid.skate.engine.utils.toRadiansF
import org.joml.Quaternionf

class BulletPhysics3D(
    private val debugRenderer: DebugRenderer
) : IPhysics3D {
    // Bullet's default filter collides when either body's mask accepts the other; layers need both to agree
    private class LayeredPhysicsSpace : PhysicsSpace(BroadphaseType.DBVT) {
        override fun needsCollision(a: PhysicsCollisionObject, b: PhysicsCollisionObject): Boolean =
            super.needsCollision(a, b) &&
                (a.collisionGroup and b.collideWithGroups) != 0 && (b.collisionGroup and a.collideWithGroups) != 0
    }

    private val physicsSpace: PhysicsSpace = LayeredPhysicsSpace()

    override var debugEnabled = false

    init {
        this.physicsSpace.setGravity(JmeVector3f(0f, -9.81f, 0f))
    }

    /**
     * Gets the current gravity vector of the physics world.
     *
     * @return The gravity vector in m/s².
     */
    override fun getGravity(): JomlVector3f {
        val g = physicsSpace.getGravity(null)
        return JomlVector3f(g.x, g.y, g.z)
    }

    /**
     * Sets the gravity vector for the physics world.
     *
     * @param gravity The new gravity vector in m/s².
     */
    override fun setGravity(gravity: JomlVector3f) {
        physicsSpace.setGravity(JmeVector3f(gravity.x, gravity.y, gravity.z))
    }

    override var contactListener: PhysicsContactListener? = null

    private val rayStart = JmeVector3f()
    private val rayEnd = JmeVector3f()
    private val rayResults = ArrayList<PhysicsRayTestResult>()

    /** Reuses scratch vectors and the result list so per-step ground checks only allocate inside Bullet. */
    override fun raycastClosest(from: JomlVector3f, to: JomlVector3f, excludeBody: IPhysicsBody3D?): Float? {
        if (debugEnabled) {
            debugRenderer.addLine3D(from, to, JomlVector3f(1f, 0f, 1f), 1)
        }
        val excluded = excludedObject(excludeBody)
        rayStart.set(from.x, from.y, from.z)
        rayEnd.set(to.x, to.y, to.z)
        rayResults.clear()
        physicsSpace.rayTest(rayStart, rayEnd, rayResults)

        var closest: Float? = null
        for (i in rayResults.indices) {
            val hit = rayResults[i]
            if (isQueryable(hit.collisionObject, ALL_LAYERS, excluded) && (closest == null || hit.hitFraction < closest)) {
                closest = hit.hitFraction
            }
        }
        return closest
    }

    override fun raycast(from: JomlVector3f, to: JomlVector3f, layerMask: Int, excludeBody: IPhysicsBody3D?): PhysicsHit? =
        raycastAll(from, to, layerMask, excludeBody).firstOrNull()

    override fun raycastAll(
        from: JomlVector3f,
        to: JomlVector3f,
        layerMask: Int,
        excludeBody: IPhysicsBody3D?,
    ): List<PhysicsHit> {
        if (debugEnabled) {
            debugRenderer.addLine3D(from, to, JomlVector3f(1f, 0f, 1f), 1)
        }

        val excluded = excludedObject(excludeBody)
        val delta = JomlVector3f(to).sub(from)
        return physicsSpace.rayTest(from.toJme(), to.toJme())
            .filter { isQueryable(it.collisionObject, layerMask, excluded) }
            .sortedBy { it.hitFraction }
            .map { hit ->
                PhysicsHit(
                    gameObject = hit.collisionObject.userObject as? GameObject,
                    point = JomlVector3f(delta).mul(hit.hitFraction).add(from),
                    normal = hit.getHitNormalLocal(null).toVector3f(),
                    fraction = hit.hitFraction,
                )
            }
    }

    override fun shapeCast(
        shape: ColliderShape,
        from: JomlVector3f,
        to: JomlVector3f,
        layerMask: Int,
        excludeBody: IPhysicsBody3D?,
    ): PhysicsHit? {
        val convex = createShape(shape) as? ConvexShape
            ?: throw IllegalArgumentException("Shape casts need a convex shape")
        val excluded = excludedObject(excludeBody)
        val delta = JomlVector3f(to).sub(from)
        val hit = physicsSpace.sweepTest(convex, translationOnly(from), translationOnly(to), ArrayList(), 0f)
            .filter { isQueryable(it.collisionObject, layerMask, excluded) }
            .minByOrNull { it.hitFraction } ?: return null
        return PhysicsHit(
            gameObject = hit.collisionObject.userObject as? GameObject,
            point = JomlVector3f(delta).mul(hit.hitFraction).add(from),
            normal = hit.getHitNormalLocal(null).toVector3f(),
            fraction = hit.hitFraction,
        )
    }

    private fun translationOnly(location: JomlVector3f) =
        com.jme3.math.Transform().also { it.translation.set(location.x, location.y, location.z) }

    private fun excludedObject(body: IPhysicsBody3D?): PhysicsCollisionObject? =
        ((body as? RigidBody3D)?.rawBody as? BulletRigidBodyHandle)?.body

    private fun isQueryable(obj: PhysicsCollisionObject, layerMask: Int, excluded: PhysicsCollisionObject?): Boolean =
        obj !== excluded && obj.isContactResponse && (obj.collisionGroup and layerMask) != 0

    /**
     * Adds a GameObject to the physics simulation.
     * It inspects the GameObject for [RigidBody3D] and [Collider3D] components,
     * creates the necessary Bullet shapes, and adds them to the [PhysicsSpace].
     *
     * @param go The GameObject to add.
     */
    override fun add(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>()
        if (rb != null) {
            val desiredMass = if (rb.bodyType == BodyType.Static) 0f else rb.mass
            
            // Check if we need to rebuild due to mass change
            val rawBody = (rb.rawBody as? BulletRigidBodyHandle)?.body

            if (rawBody != null) {
                if (rawBody.mass != desiredMass) {
                    physicsSpace.remove(rawBody)
                    contactBodies.remove(rawBody)
                    rb.rawBody = null
                } else {
                    // Body exists and mass is correct, just sync properties
                    syncBodyProperties(rawBody, rb, go)
                    return
                }
            }

            val colliders = go.components.filterIsInstance<Collider3D>()
            val shape: CollisionShape = if (colliders.size == 1 && colliders[0].offset.lengthSquared() == 0f) {
                // Bullet only sweeps convex shapes for CCD, so avoid wrapping a lone collider in a compound
                createShape(colliders[0].toShape())
            } else {
                val compound = CompoundCollisionShape()
                colliders.forEach { c ->
                    compound.addChildShape(createShape(c.toShape()), JmeVector3f(c.offset.x, c.offset.y, c.offset.z))
                }

                // If no colliders, provide a default box
                if (colliders.isEmpty()) {
                    val box = BoxCollisionShape(JmeVector3f(1f, 1f, 1f))
                    box.margin = 0.04f
                    compound.addChildShape(box, JmeVector3f(0f, 0f, 0f))
                }
                compound
            }

            val body = PhysicsRigidBody(shape, desiredMass)
            rb.rawBody = BulletRigidBodyHandle(body)

            if (rb.bodyType == BodyType.Kinematic) {
                body.isKinematic = true
            }

            val handle = rb.rawBody!!
            handle.restitution = rb.restitution
            handle.collisionLayer = rb.collisionLayer
            handle.collisionMask = rb.collisionMask
            handle.isTrigger = rb.isTrigger
            if (rb.useCCD) {
                val radius = ccdRadius(colliders, go.getComponent<Transform>()?.scale ?: JomlVector3f(1f))
                handle.setContinuousCollision(radius, radius)
            }
            body.userObject = go
            physicsSpace.add(body)
            contactBodies.add(body)
            syncBodyProperties(body, rb, go)// Initial property sync
        }
    }


    /**
     * Synchronizes properties from our component-based representation ([RigidBody3D], [GameObject] transform)
     * to the underlying Bullet [PhysicsRigidBody].
     *
     * @param body The raw Bullet rigid body to update.
     * @param rb The component containing the desired physics properties (friction, damping).
     * @param go The game object providing the transform (position, rotation, scale).
     */
    private fun syncBodyProperties(body: PhysicsRigidBody, rb: RigidBody3D, go: GameObject) {
        val transform = go.getComponent<Transform>() ?: return
        val trans = transform.translation
        val rot = transform.rotation
        val scale = transform.scale

        body.setPhysicsLocation(JmeVector3f(trans.x, trans.y, trans.z))
        body.friction = rb.friction
        body.restitution = rb.restitution
        body.setDamping(rb.linearDamping, rb.angularDamping)

        val q = Quaternionf().rotationXYZ(
            rot.x.toRadiansF(),
            rot.y.toRadiansF(),
            rot.z.toRadiansF()
        )
        body.setPhysicsRotation(Quaternion(q.x, q.y, q.z, q.w))

        val collider = go.getComponent<BoxCollider3D>() ?: return
        body.collisionShape.margin = collider.margin
        body.collisionShape.setScale(JmeVector3f(scale.x, scale.y, scale.z))
    }

    /**
     * Removes a GameObject from the physics simulation.
     * It destroys the associated Bullet rigid body.
     *
     * @param go The GameObject to remove.
     */
    override fun remove(go: GameObject) {
        forgetContacts(go)
        val rb = go.getComponent<RigidBody3D>()
        rb?.rawBody?.let {
            (it as? BulletRigidBodyHandle)?.let { handle ->
                physicsSpace.remove(handle.body)
                contactBodies.remove(handle.body)
            }
            rb.rawBody = null
        }
    }

    override fun createRigidBody(shape: ColliderShape, mass: Float): IRigidBodyHandle {
        val body = PhysicsRigidBody(createShape(shape), mass)
        physicsSpace.add(body)
        return BulletRigidBodyHandle(body)
    }

    override fun removeRigidBody(handle: IRigidBodyHandle) {
        (handle as? BulletRigidBodyHandle)?.let { physicsSpace.remove(it.body) }
    }

    override fun createStaticAnchor(location: JomlVector3f): IRigidBodyHandle {
        val body = PhysicsRigidBody(BoxCollisionShape(0.01f), 0f)
        body.setPhysicsLocation(location.toJme())
        physicsSpace.add(body)
        return BulletRigidBodyHandle(body)
    }

    private fun createShape(shape: ColliderShape): CollisionShape = when (shape) {
        is ColliderShape.Box -> BoxCollisionShape(shape.halfExtents.toJme()).also { it.margin = shape.margin }
        is ColliderShape.Capsule -> CapsuleCollisionShape(shape.radius, shape.height, shape.axis)
        is ColliderShape.Cylinder ->
            CylinderCollisionShape(shape.radius, shape.height, shape.axis).also { it.margin = shape.margin }
        is ColliderShape.ConvexHull -> HullCollisionShape(*shape.points.map { it.toJme() }.toTypedArray())
    }

    override fun addConstraint(constraint: IPhysicsConstraint) {
        val joint = (constraint.rawJoint as? PhysicsJoint) ?: createJoint(constraint)
        physicsSpace.add(joint)
    }

    override fun removeConstraint(constraint: IPhysicsConstraint) {
        (constraint.rawJoint as? PhysicsJoint)?.let {
            physicsSpace.remove(it)
        }
    }

    private fun bodyOf(handle: IRigidBodyHandle): PhysicsRigidBody =
        (handle as? BulletRigidBodyHandle)?.body
            ?: throw IllegalArgumentException("Rigid body was not created by the Bullet backend")

    // Slider joints slide along the frame's X axis, so build a frame that maps X onto the requested axis
    private fun xAxisFrame(axis: org.joml.Vector3f): Matrix3f {
        val dir = org.joml.Vector3f(axis).normalize()
        return org.joml.Quaternionf().rotationTo(org.joml.Vector3f(1f, 0f, 0f), dir)
            .get(org.joml.Matrix3f()).toJme()
    }

    private fun createJoint(constraint: IPhysicsConstraint): PhysicsJoint {
        val joint: PhysicsJoint = when (constraint) {
            is HingeConstraint3D -> HingeJoint(
                bodyOf(constraint.bodyA), bodyOf(constraint.bodyB),
                constraint.pivotA.toJme(), constraint.pivotB.toJme(),
                constraint.axisA.toJme(), constraint.axisB.toJme()
            ).also { it.setLimit(constraint.limitMin, constraint.limitMax) }

            is ConeTwistConstraint3D -> ConeJoint(
                bodyOf(constraint.bodyA), bodyOf(constraint.bodyB),
                constraint.pivotA.toJme(), constraint.pivotB.toJme(),
                constraint.rotA.toJme(), constraint.rotB.toJme()
            ).also { it.setLimit(constraint.swingSpan1, constraint.swingSpan2, constraint.twistSpan) }

            is FixedConstraint3D -> SixDofJoint(
                bodyOf(constraint.bodyA), bodyOf(constraint.bodyB),
                constraint.pivotA.toJme(), constraint.pivotB.toJme(),
                Matrix3f.IDENTITY.clone(), Matrix3f.IDENTITY.clone(), true
            ).also {
                it.setLinearLowerLimit(JmeVector3f.ZERO.clone())
                it.setLinearUpperLimit(JmeVector3f.ZERO.clone())
                it.setAngularLowerLimit(JmeVector3f.ZERO.clone())
                it.setAngularUpperLimit(JmeVector3f.ZERO.clone())
            }

            is SixDofConstraint3D -> SixDofJoint(
                bodyOf(constraint.bodyA), bodyOf(constraint.bodyB),
                constraint.pivotA.toJme(), constraint.pivotB.toJme(),
                Matrix3f.IDENTITY.clone(), Matrix3f.IDENTITY.clone(), true
            ).also {
                it.setLinearLowerLimit(constraint.linearLower.toJme())
                it.setLinearUpperLimit(constraint.linearUpper.toJme())
                it.setAngularLowerLimit(constraint.angularLower.toJme())
                it.setAngularUpperLimit(constraint.angularUpper.toJme())
            }

            is SliderConstraint3D -> SliderJoint(
                bodyOf(constraint.bodyA), bodyOf(constraint.bodyB),
                constraint.pivotA.toJme(), constraint.pivotB.toJme(),
                xAxisFrame(constraint.axisA), xAxisFrame(constraint.axisB), true
            ).also {
                it.setLowerLinLimit(constraint.lowerLimit)
                it.setUpperLinLimit(constraint.upperLimit)
            }

            else -> throw IllegalArgumentException("Unsupported constraint ${constraint::class.simpleName}")
        }
        constraint.rawJoint = joint
        return joint
    }

    /** Advances the simulation by one fixed step; the caller owns the time accumulation. */
    override fun step(fixedDt: Float) {
        if (!fixedDt.isFinite() || fixedDt <= 0f) return

        val startTime = System.nanoTime()
        physicsSpace.update(fixedDt, 0)
        EngineStats.physicsStepTime.set(System.nanoTime() - startTime)
        dispatchContacts()
    }

    /** Half of the thinnest collider dimension: a swept sphere this size cannot skip over geometry thinner than the body. */
    private fun ccdRadius(colliders: List<Collider3D>, scale: JomlVector3f): Float {
        val smallest = colliders.minOfOrNull {
            when (val shape = it.toShape()) {
                is ColliderShape.Box ->
                    minOf(shape.halfExtents.x * scale.x, shape.halfExtents.y * scale.y, shape.halfExtents.z * scale.z)
                is ColliderShape.Capsule -> shape.radius * minOf(scale.x, scale.z)
                is ColliderShape.Cylinder -> minOf(shape.radius * minOf(scale.x, scale.z), shape.height / 2f * scale.y)
                is ColliderShape.ConvexHull -> 0.1f
            }
        } ?: 0.1f
        return (smallest * 0.5f).coerceAtLeast(0.01f)
    }

    private class PendingContact {
        var kind = 0
        lateinit var a: GameObject
        lateinit var b: GameObject
        val point = JomlVector3f()
        val normal = JomlVector3f()
        var impulse = 0f
    }

    private val contactBodies = ArrayList<PhysicsRigidBody>()
    private val pending = ArrayList<PendingContact>()
    private var pendingCount = 0
    private var collisionsNow = LongSet()
    private var collisionsPrev = LongSet()
    private var overlapsNow = LongSet()
    private var overlapsPrev = LongSet()
    private val overlapObjects = HashMap<Long, Pair<GameObject, GameObject>>()

    // Updated per contactTest so a single listener instance serves every body
    private var testBody: PhysicsRigidBody? = null
    private var testSelf: GameObject? = null
    private var testIsTrigger = false

    private val contactCollector = PhysicsCollisionListener { event ->
        val body = testBody ?: return@PhysicsCollisionListener
        val self = testSelf ?: return@PhysicsCollisionListener
        val bodyIsA = event.objectA === body
        val other = if (bodyIsA) event.objectB else event.objectA
        val otherGo = other.userObject as? GameObject ?: return@PhysicsCollisionListener
        if (!other.isContactResponse) return@PhysicsCollisionListener

        if (testIsTrigger) {
            val key = (self.uId.toLong() shl 32) or otherGo.uId.toLong()
            overlapsNow.add(key)
            if (!overlapsPrev.contains(key)) {
                overlapObjects[key] = self to otherGo
                queue(PENDING_TRIGGER_ENTER, self, otherGo)
            }
        } else {
            val key = (minOf(self.uId, otherGo.uId).toLong() shl 32) or maxOf(self.uId, otherGo.uId).toLong()
            if (collisionsNow.add(key) && !collisionsPrev.contains(key)) {
                val contact = queue(PENDING_COLLISION, self, otherGo)
                event.getPositionWorldOnB(tempContactVec)
                contact.point.set(tempContactVec.x, tempContactVec.y, tempContactVec.z)
                event.getNormalWorldOnB(tempContactVec)
                contact.normal.set(tempContactVec.x, tempContactVec.y, tempContactVec.z)
                if (!bodyIsA) contact.normal.negate()
                contact.impulse = event.appliedImpulse
            }
        }
    }
    private val tempContactVec = JmeVector3f()

    private fun queue(kind: Int, a: GameObject, b: GameObject): PendingContact {
        if (pendingCount == pending.size) pending.add(PendingContact())
        return pending[pendingCount++].also {
            it.kind = kind
            it.a = a
            it.b = b
        }
    }

    private fun dispatchContacts() {
        val listener = contactListener ?: return
        collisionsPrev = collisionsNow.also { collisionsNow = collisionsPrev }
        collisionsNow.clear()
        overlapsPrev = overlapsNow.also { overlapsNow = overlapsPrev }
        overlapsNow.clear()
        pendingCount = 0

        for (i in contactBodies.indices) {
            val body = contactBodies[i]
            val self = body.userObject as? GameObject ?: continue
            testIsTrigger = !body.isContactResponse
            if (!testIsTrigger && body.isStatic) continue
            testBody = body
            testSelf = self
            physicsSpace.contactTest(body, contactCollector)
        }
        testBody = null
        testSelf = null

        overlapsPrev.forEach { key ->
            if (!overlapsNow.contains(key)) overlapObjects.remove(key)?.let { queue(PENDING_TRIGGER_EXIT, it.first, it.second) }
        }

        // Dispatch last: listeners may add or remove bodies, which touches the sets above
        for (i in 0 until pendingCount) {
            val c = pending[i]
            when (c.kind) {
                PENDING_COLLISION -> listener.onCollisionStarted(c.a, c.b, c.point, c.normal, c.impulse)
                PENDING_TRIGGER_ENTER -> listener.onTriggerEnter(c.a, c.b)
                else -> listener.onTriggerExit(c.a, c.b)
            }
        }
        pendingCount = 0
    }

    /** Drops contact state for a removed object, reporting trigger exits so listeners do not keep stale overlaps. */
    private fun forgetContacts(go: GameObject) {
        val gone = overlapObjects.filterValues { it.first === go || it.second === go }
        gone.forEach { (key, pair) ->
            overlapObjects.remove(key)
            overlapsNow.retainWhere { it != key }
            if (pair.second === go) contactListener?.onTriggerExit(pair.first, go)
        }
        collisionsNow.retainWhere { (it shr 32).toInt() != go.uId && (it and 0xFFFFFFFFL).toInt() != go.uId }
    }

    private companion object {
        const val PENDING_COLLISION = 0
        const val PENDING_TRIGGER_ENTER = 1
        const val PENDING_TRIGGER_EXIT = 2
    }

    override fun debugDraw(dt: Float) {
        if (debugEnabled) {
            val debugColor = JomlVector3f(0f, 1f, 0f)
            physicsSpace.rigidBodyList.forEach { body ->
                val location = body.getPhysicsLocation(null)
                val rotation = body.getPhysicsRotation(null)

                val pos = JomlVector3f(location.x, location.y, location.z)
                val rot = Quaternionf(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW())

                debugDrawShape(body.collisionShape, pos, rot, debugColor)
            }
        }
    }

    /**
     * Dispatches the correct debug drawing method based on the [CollisionShape]'s type.
     *
     * @param shape The collision shape to draw.
     * @param pos The world position of the shape.
     * @param rot The world rotation of the shape.
     * @param color The color to use for drawing.
     */
    private fun debugDrawShape(shape: CollisionShape, pos: JomlVector3f, rot: Quaternionf, color: JomlVector3f) {
        when (shape) {
            is BoxCollisionShape -> drawBoxCollisionShape(shape, pos, rot, color)
            is CylinderCollisionShape -> drawCylinderCollisionShape(shape, pos, rot, color)
            is CapsuleCollisionShape -> drawCapsuleCollisionShape(shape, pos, rot, color)
            is CompoundCollisionShape -> drawCompoundCollisionShape(shape, pos, rot, color)
            is HullCollisionShape, is MeshCollisionShape -> drawComplexShapes(shape, pos, rot, color)
        }
    }

    /**
     * Draws a placeholder for complex collision shapes (like meshes or hulls) since rendering them
     * vertex-by-vertex would be too slow for a simple debug view.
     *
     * @param shape The complex collision shape.
     * @param pos The world position.
     * @param rot The world rotation.
     * @param color The drawing color.
     */
    private fun drawComplexShapes(
        shape: CollisionShape,
        pos: JomlVector3f,
        rot: Quaternionf,
        color: JomlVector3f
    ) {
        // Complex shapes - just draw a small cross for now to indicate position
        debugRenderer.addLine3D(JomlVector3f(pos).add(-0.5f, 0f, 0f), JomlVector3f(pos).add(0.5f, 0f, 0f), color)
        debugRenderer.addLine3D(JomlVector3f(pos).add(0f, -0.5f, 0f), JomlVector3f(pos).add(0f, 0.5f, 0f), color)
        debugRenderer.addLine3D(JomlVector3f(pos).add(0f, 0f, -0.5f), JomlVector3f(pos).add(0f, 0f, 0.5f), color)
    }

    /**
     * Recursively draws the child shapes of a [CompoundCollisionShape].
     *
     * @param shape The compound shape to draw.
     * @param pos The world position of the compound shape's parent.
     * @param rot The world rotation of the compound shape's parent.
     * @param color The drawing color.
     */
    private fun drawCompoundCollisionShape(
        shape: CompoundCollisionShape,
        pos: JomlVector3f,
        rot: Quaternionf,
        color: JomlVector3f
    ) {
        shape.listChildren().forEach { child ->
            val childShape = child.shape
            val childOffset = child.copyOffset(null)
            val childRot = child.copyRotation(null)

            // Child's local to world: WorldPos + WorldRot * (ChildOffset + ChildRot * LocalPoint)
            // We can combine them into a single transform
            val combinedRot =
                Quaternionf(rot).mul(Quaternionf(childRot.getX(), childRot.getY(), childRot.getZ(), childRot.getW()))
            val combinedPos = JomlVector3f(childOffset.x, childOffset.y, childOffset.z)
            rot.transform(combinedPos)
            combinedPos.add(pos)

            debugDrawShape(childShape, combinedPos, combinedRot, color)
        }
    }

    /**
     * Draws a wireframe representation of a [CylinderCollisionShape].
     *
     * @param shape The cylinder shape.
     * @param pos The world position.
     * @param rot The world rotation.
     * @param color The drawing color.
     */
    private fun drawCylinderCollisionShape(shape: CylinderCollisionShape, pos: JomlVector3f, rot: Quaternionf, color: JomlVector3f) {
        val axis = shape.axis
        val radius: Float
        val height: Float
        val halfExtents = shape.getHalfExtents(null)
        val scale = shape.getScale(null)

        // Apply scale to half-extents for correct debug visualization
        val scaledHalfExtents = JmeVector3f(
            halfExtents.x * scale.x,
            halfExtents.y * scale.y,
            halfExtents.z * scale.z
        )

        when (axis) {
            0 -> { // X-axis is height
                radius = scaledHalfExtents.y
                height = scaledHalfExtents.x * 2f
            }

            1 -> { // Y-axis is height
                radius = scaledHalfExtents.x
                height = scaledHalfExtents.y * 2f
            }

            2 -> { // Z-axis is height
                radius = scaledHalfExtents.x
                height = scaledHalfExtents.z * 2f
            }

            else -> {
                radius = scaledHalfExtents.x
                height = scaledHalfExtents.y * 2f
            }
        }
        debugRenderer.addCylinder3D(pos, rot, radius, height, axis, color)
    }

    /**
     * Draws a wireframe representation of a [CapsuleCollisionShape].
     *
     * @param shape The capsule shape.
     * @param pos The world position.
     * @param rot The world rotation.
     * @param color The drawing color.
     */
    private fun drawCapsuleCollisionShape(
        shape: CapsuleCollisionShape,
        pos: JomlVector3f,
        rot: Quaternionf,
        color: JomlVector3f
    ) {
        val axis = shape.axis
        val radius = shape.radius
        val height = shape.height // In Bullet, this is usually the height of the cylinder part

        // For debug drawing, a cylinder provides an approximate visualization of the capsule.
        // Rendering full spheres at the ends is often overkill for wireframe debug view.
        // The total height of capsule in bullet is height + 2 * radius
        debugRenderer.addCylinder3D(pos, rot, radius, height + 2f * radius, axis, color)
    }

    /**
     * Draws a wireframe representation of a [BoxCollisionShape].
     *
     * @param shape The box shape.
     * @param pos The world position.
     * @param rot The world rotation.
     * @param color The drawing color.
     */
    private fun drawBoxCollisionShape(shape: BoxCollisionShape, pos: JomlVector3f, rot: Quaternionf, color: JomlVector3f) {
        val halfExtents = shape.getHalfExtents(null)
        val scale = shape.getScale(null)

        // Apply scale to half-extents for correct debug visualization
        val scaledHalfExtents = JomlVector3f(
            halfExtents.x * scale.x,
            halfExtents.y * scale.y,
            halfExtents.z * scale.z
        )

        debugRenderer.addBox3D(pos, rot, scaledHalfExtents, color)
    }

    override fun destroy() {
        physicsSpace.destroy()
    }
}
