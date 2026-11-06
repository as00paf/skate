package com.pafoid.skate.engine.physics3d.jolt

import com.github.stephengold.joltjni.AllHitCastRayCollector
import com.github.stephengold.joltjni.AllHitCastShapeCollector
import com.github.stephengold.joltjni.BodyCreationSettings
import com.github.stephengold.joltjni.BodyFilter
import com.github.stephengold.joltjni.BodyInterface
import com.github.stephengold.joltjni.BroadPhaseLayerFilter
import com.github.stephengold.joltjni.CombineFunction
import com.github.stephengold.joltjni.JobSystemThreadPool
import com.github.stephengold.joltjni.Jolt
import com.github.stephengold.joltjni.ObjectLayerFilter
import com.github.stephengold.joltjni.PhysicsSystem
import com.github.stephengold.joltjni.Quat
import com.github.stephengold.joltjni.RMat44
import com.github.stephengold.joltjni.RRayCast
import com.github.stephengold.joltjni.RShapeCast
import com.github.stephengold.joltjni.RVec3
import com.github.stephengold.joltjni.RayCastSettings
import com.github.stephengold.joltjni.ShapeCastSettings
import com.github.stephengold.joltjni.ShapeFilter
import com.github.stephengold.joltjni.ShapeRefC
import com.github.stephengold.joltjni.TempAllocatorImpl
import com.github.stephengold.joltjni.Vec3
import com.github.stephengold.joltjni.enumerate.EActivation
import com.github.stephengold.joltjni.enumerate.EBackFaceMode
import com.github.stephengold.joltjni.enumerate.EMotionQuality
import com.github.stephengold.joltjni.enumerate.EMotionType
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.ALL_LAYERS
import com.pafoid.skate.engine.physics3d.BodyDescription
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.engine.physics3d.PhysicsContactListener
import com.pafoid.skate.engine.physics3d.PhysicsHit
import com.pafoid.skate.engine.physics3d.constraints.IPhysicsConstraint
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Jolt Physics backend (PHY-17), created only by `Physics3DFactory` after [JoltRuntime.ensureLoaded].
 *
 * Implemented: bodies from [BodyDescription], shapes, layer/mask filtering, triggers as sensors, CCD, stepping,
 * teleport, standalone bodies and anchors, ray and shape queries (slice b), collision and trigger events through
 * [JoltContacts] (slice c). Joints (PHY-17d) and debug draw (PHY-17e) come later. Not thread-safe: call it from the
 * thread that steps it.
 */
class JoltPhysics3D internal constructor(
    private val debugRenderer: DebugRenderer,
    threads: Int = defaultThreadCount(),
) : IPhysics3D {
    private val layers = JoltLayers()
    private val tempAllocator = TempAllocatorImpl(TEMP_ALLOCATOR_BYTES)
    private val jobSystem = JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, threads)
    private val system = PhysicsSystem().init(
        MAX_BODIES, 0, MAX_BODY_PAIRS, MAX_CONTACT_CONSTRAINTS,
        layers.broadPhaseLayers, layers.objectVsBroadPhase, layers.pairFilter,
    )
    private val bodies: BodyInterface = system.bodyInterfaceNoLock
    private val handles = HashMap<Int, JoltRigidBodyHandle>()
    private val contacts = JoltContacts { handles[it] }

    // Bullet combines friction and restitution as a product; match it so material values mean the same
    private val combineFriction = CombineFunction.product(true)
    private val combineRestitution = CombineFunction.product(false)

    // Reused query objects (default filters accept everything; layer, trigger and exclusion filtering is done here)
    private val broadPhaseFilter = BroadPhaseLayerFilter()
    private val objectLayerFilter = ObjectLayerFilter()
    private val bodyFilter = BodyFilter()
    private val shapeFilter = ShapeFilter()
    private val rayCollector = AllHitCastRayCollector()
    private val shapeCollector = AllHitCastShapeCollector()
    private val raySettings = RayCastSettings().apply {
        // Bullet: triangle meshes are two-sided, and a ray starting inside a convex body does not hit it
        setBackFaceModeTriangles(EBackFaceMode.CollideWithBackFaces)
        setTreatConvexAsSolid(false)
    }
    private val shapeCastSettings = ShapeCastSettings().apply {
        setBackFaceModeTriangles(EBackFaceMode.CollideWithBackFaces)
    }
    private val origin = RVec3()
    private val tempR = RVec3()
    private val tempQ = Quaternionf()
    private var destroyed = false

    override var debugEnabled = false

    override var contactListener: PhysicsContactListener? = null

    override val rigidBodyCount: Int get() = system.numBodies

    init {
        system.setGravity(0f, -9.81f, 0f)
        system.setCombineFriction(combineFriction)
        system.setCombineRestitution(combineRestitution)
        system.setContactListener(contacts.listener)
    }

    override fun getGravity(): Vector3f = system.gravity.let { Vector3f(it.x, it.y, it.z) }

    override fun setGravity(gravity: Vector3f) {
        system.setGravity(gravity.x, gravity.y, gravity.z)
    }

    // --- bodies ---

    override fun add(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>() ?: return
        val desc = BodyDescription.of(go) ?: return
        (rb.rawBody as? JoltRigidBodyHandle)?.let { existing ->
            if (existing.mass == desc.mass && existing.motionType == desc.motionType) {
                existing.setPose(desc.position, desc.rotation, clearMotion = false)
                syncMaterial(existing, rb)
                return
            }
            // Build the replacement first so its contact pairs carry over before the old body goes
            val rebuilt = createBody(desc, go)
            contacts.rebind(existing.id, rebuilt.id)
            destroyBody(existing, report = false)
            rb.rawBody = rebuilt
            return
        }
        rb.rawBody = createBody(desc, go)
    }

    private fun createBody(desc: BodyDescription, go: GameObject): JoltRigidBodyHandle {
        val shape = JoltShapes.body(desc.parts, desc.scale)
        val moving = desc.motionType != BodyType.Static
        // Like Bullet, a dynamic trigger is held kinematic: triggers detect, they are never pushed
        val nativeType = if (desc.isTrigger && desc.motionType == BodyType.Dynamic) BodyType.Kinematic else desc.motionType
        val settings = BodyCreationSettings(
            shape,
            RVec3(desc.position.x.toDouble(), desc.position.y.toDouble(), desc.position.z.toDouble()),
            Quat(desc.rotation.x, desc.rotation.y, desc.rotation.z, desc.rotation.w),
            motionTypeOf(nativeType),
            layers.objectLayer(desc.collisionLayer, desc.collisionMask, moving),
        )
        settings.setFriction(desc.friction)
        settings.setRestitution(desc.restitution)
        settings.setLinearDamping(JoltConversions.damping(desc.linearDamping))
        settings.setAngularDamping(JoltConversions.damping(desc.angularDamping))
        settings.setIsSensor(desc.isTrigger)
        if (moving) dynamicCapable(settings, desc.mass)
        if (desc.ccdRadius != null) settings.setMotionQuality(EMotionQuality.LinearCast)
        return addBody(settings, shape, desc.motionType, desc.mass, desc.collisionLayer, desc.collisionMask,
            desc.isTrigger, desc.linearDamping, desc.angularDamping, go)
    }

    /**
     * Lets a moving body switch between kinematic and dynamic, with its mass set and inertia computed from the shape.
     * Kinematic bodies also touch static and kinematic ones, so contacts and triggers report them like Bullet does.
     */
    private fun dynamicCapable(settings: BodyCreationSettings, mass: Float) {
        settings.setAllowDynamicOrKinematic(true)
        settings.setCollideKinematicVsNonDynamic(true)
        settings.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia)
        settings.massPropertiesOverride.setMass(if (mass > 0f) mass else 1f)
    }

    private fun addBody(
        settings: BodyCreationSettings,
        shape: ShapeRefC,
        motionType: BodyType,
        mass: Float,
        layer: Int,
        mask: Int,
        trigger: Boolean,
        linearDamping: Float,
        angularDamping: Float,
        go: GameObject?,
    ): JoltRigidBodyHandle {
        try {
            val body = bodies.createBody(settings)
            bodies.addBody(body.id, EActivation.Activate)
            val handle = JoltRigidBodyHandle(
                bodies, layers, body, motionType, mass, layer, mask, trigger, linearDamping, angularDamping, go,
            )
            handles[handle.id] = handle
            return handle
        } finally {
            settings.close()
            shape.close()
        }
    }

    private fun syncMaterial(handle: JoltRigidBodyHandle, rb: RigidBody3D) {
        handle.friction = rb.friction
        handle.restitution = rb.restitution
        handle.setDamping(rb.linearDamping, rb.angularDamping)
    }

    override fun teleport(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>() ?: return
        val handle = rb.rawBody as? JoltRigidBodyHandle ?: return add(go)
        val transform = go.getComponent<Transform>() ?: return
        handle.setPose(transform.translation, tempQ.set(BodyDescription.rotationOf(transform)), clearMotion = true)
        syncMaterial(handle, rb)
    }

    override fun remove(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>() ?: return
        (rb.rawBody as? JoltRigidBodyHandle)?.let { destroyBody(it) }
        rb.rawBody = null
    }

    /** Standalone bodies use Bullet's defaults for them: layer 1, colliding with layer 1 only. */
    override fun createRigidBody(shape: ColliderShape, mass: Float): IRigidBodyHandle {
        val joltShape = JoltShapes.create(shape)
        val type = if (mass > 0f) BodyType.Dynamic else BodyType.Static
        val settings = BodyCreationSettings(
            joltShape, RVec3(), Quat(0f, 0f, 0f, 1f), motionTypeOf(type),
            layers.objectLayer(STANDALONE_LAYER, STANDALONE_LAYER, type != BodyType.Static),
        )
        if (type != BodyType.Static) dynamicCapable(settings, mass)
        return addBody(settings, joltShape, type, mass, STANDALONE_LAYER, STANDALONE_LAYER, false, 0f, 0f, null)
    }

    /** A pivot, not an obstacle: a tiny static sensor, so it never pushes the body jointed to it. */
    override fun createStaticAnchor(location: Vector3f): IRigidBodyHandle {
        val joltShape = JoltShapes.create(ColliderShape.Box(Vector3f(ANCHOR_HALF_EXTENT), 0f))
        val settings = BodyCreationSettings(
            joltShape,
            RVec3(location.x.toDouble(), location.y.toDouble(), location.z.toDouble()),
            Quat(0f, 0f, 0f, 1f), EMotionType.Static,
            layers.objectLayer(STANDALONE_LAYER, STANDALONE_LAYER, false),
        )
        settings.setIsSensor(true)
        return addBody(settings, joltShape, BodyType.Static, 0f, STANDALONE_LAYER, STANDALONE_LAYER, true, 0f, 0f, null)
    }

    override fun removeRigidBody(handle: IRigidBodyHandle) {
        (handle as? JoltRigidBodyHandle)?.let { destroyBody(it) }
    }

    /** With [report], a body leaving a trigger makes it report an exit (as Bullet does on removal). */
    private fun destroyBody(handle: JoltRigidBodyHandle, report: Boolean = true) {
        if (!handles.containsKey(handle.id)) return
        contacts.forget(handle, if (report) contactListener else null)
        handles.remove(handle.id)
        bodies.removeBody(handle.id)
        bodies.destroyBody(handle.id)
    }

    // --- joints (PHY-17d) ---

    override fun addConstraint(constraint: IPhysicsConstraint) =
        throw UnsupportedOperationException("Jolt joints are not implemented yet (PHY-17d)")

    override fun removeConstraint(constraint: IPhysicsConstraint) {}

    // --- simulation ---

    override fun step(fixedDt: Float) {
        if (destroyed || !fixedDt.isFinite() || fixedDt <= 0f) return
        val start = System.nanoTime()
        system.update(fixedDt, 1, tempAllocator, jobSystem)
        EngineStats.physicsStepTime.set(System.nanoTime() - start)
        contacts.afterStep(contactListener)
    }

    /** Debug shapes arrive with PHY-17e. */
    override fun debugDraw(dt: Float) {}

    // --- queries ---

    override fun raycastClosest(from: Vector3f, to: Vector3f, excludeBody: IPhysicsBody3D?): Float? {
        castRay(from, to)
        val excluded = excludedHandle(excludeBody)
        var closest: Float? = null
        for (i in 0 until rayCollector.countHits()) {
            val hit = rayCollector.get(i)
            val handle = handles[hit.bodyId] ?: continue
            if (isQueryable(handle, ALL_LAYERS, excluded) && (closest == null || hit.fraction < closest)) closest = hit.fraction
        }
        return closest
    }

    override fun raycast(from: Vector3f, to: Vector3f, layerMask: Int, excludeBody: IPhysicsBody3D?): PhysicsHit? =
        raycastAll(from, to, layerMask, excludeBody).firstOrNull()

    override fun raycastAll(from: Vector3f, to: Vector3f, layerMask: Int, excludeBody: IPhysicsBody3D?): List<PhysicsHit> {
        castRay(from, to)
        rayCollector.sort()
        val excluded = excludedHandle(excludeBody)
        val result = ArrayList<PhysicsHit>()
        for (i in 0 until rayCollector.countHits()) {
            val hit = rayCollector.get(i)
            val handle = handles[hit.bodyId] ?: continue
            if (!isQueryable(handle, layerMask, excluded)) continue
            val point = Vector3f(to).sub(from).mul(hit.fraction).add(from)
            tempR.set(point.x.toDouble(), point.y.toDouble(), point.z.toDouble())
            val normal = handle.body.getWorldSpaceSurfaceNormal(hit.subShapeId2, tempR)
            result += PhysicsHit(handle.gameObject, point, Vector3f(normal.x, normal.y, normal.z), hit.fraction)
        }
        return result
    }

    private fun castRay(from: Vector3f, to: Vector3f) {
        if (debugEnabled) debugRenderer.addLine3D(from, to, Vector3f(1f, 0f, 1f), 1)
        rayCollector.reset()
        val ray = RRayCast(
            RVec3(from.x.toDouble(), from.y.toDouble(), from.z.toDouble()),
            Vec3(to.x - from.x, to.y - from.y, to.z - from.z),
        )
        try {
            system.narrowPhaseQueryNoLock.castRay(
                ray, raySettings, rayCollector, broadPhaseFilter, objectLayerFilter, bodyFilter, shapeFilter,
            )
        } finally {
            ray.close()
        }
    }

    override fun shapeCast(
        shape: ColliderShape,
        from: Vector3f,
        to: Vector3f,
        layerMask: Int,
        excludeBody: IPhysicsBody3D?,
    ): PhysicsHit? {
        require(shape !is ColliderShape.TriangleMesh && shape !is ColliderShape.CompoundHull) { "Shape casts need a convex shape" }
        val castShape = JoltShapes.create(shape)
        val excluded = excludedHandle(excludeBody)
        shapeCollector.reset()
        val com = castShape.centerOfMass
        // The cast moves the shape's centre of mass; start it where the shape's origin is at [from]
        val start = RMat44.sRotationTranslation(
            Quat(0f, 0f, 0f, 1f),
            RVec3((from.x + com.x).toDouble(), (from.y + com.y).toDouble(), (from.z + com.z).toDouble()),
        )
        val cast = RShapeCast(castShape, Vec3(1f, 1f, 1f), start, Vec3(to.x - from.x, to.y - from.y, to.z - from.z))
        try {
            origin.set(0.0, 0.0, 0.0)
            system.narrowPhaseQueryNoLock.castShape(
                cast, shapeCastSettings, origin, shapeCollector, broadPhaseFilter, objectLayerFilter, bodyFilter, shapeFilter,
            )
        } finally {
            cast.close()
            start.close()
            castShape.close()
        }

        var best: JoltRigidBodyHandle? = null
        var bestFraction = Float.MAX_VALUE
        val normal = Vector3f()
        for (i in 0 until shapeCollector.countHits()) {
            val hit = shapeCollector.get(i)
            val handle = handles[hit.bodyId2] ?: continue
            if (!isQueryable(handle, layerMask, excluded) || hit.fraction >= bestFraction) continue
            best = handle
            bestFraction = hit.fraction
            // The penetration axis points from the cast shape into the hit body; the surface normal faces back
            val axis = hit.penetrationAxis
            normal.set(-axis.x, -axis.y, -axis.z)
        }
        val hitHandle = best ?: return null
        if (normal.lengthSquared() > 0f) normal.normalize()
        return PhysicsHit(hitHandle.gameObject, Vector3f(to).sub(from).mul(bestFraction).add(from), normal, bestFraction)
    }

    private fun excludedHandle(body: IPhysicsBody3D?): JoltRigidBodyHandle? =
        (body as? RigidBody3D)?.rawBody as? JoltRigidBodyHandle

    /** Same rule as the Bullet backend: not excluded, not a trigger, and on a layer in [layerMask]. */
    private fun isQueryable(handle: JoltRigidBodyHandle, layerMask: Int, excluded: JoltRigidBodyHandle?): Boolean =
        handle !== excluded && !handle.isTrigger && (handle.collisionLayer and layerMask) != 0

    // --- lifecycle ---

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        handles.values.toList().forEach { destroyBody(it, report = false) }
        system.close()
        jobSystem.close()
        tempAllocator.close()
        layers.close()
        contacts.close()
        listOf(
            rayCollector, shapeCollector, raySettings, shapeCastSettings,
            broadPhaseFilter, objectLayerFilter, bodyFilter, shapeFilter,
        ).forEach { it.close() }
    }

    private fun motionTypeOf(type: BodyType) = when (type) {
        BodyType.Static -> EMotionType.Static
        BodyType.Kinematic -> EMotionType.Kinematic
        BodyType.Dynamic -> EMotionType.Dynamic
    }

    internal companion object {
        const val MAX_BODIES = 65_536
        const val MAX_BODY_PAIRS = 65_536
        const val MAX_CONTACT_CONSTRAINTS = 20_480
        const val TEMP_ALLOCATOR_BYTES = 16 * 1024 * 1024
        const val STANDALONE_LAYER = 1
        const val ANCHOR_HALF_EXTENT = 0.01f
        const val THREADS_PROPERTY = "skate.jolt.threads"

        /** Solver threads: the `skate.jolt.threads` system property, else one less than the cores, between 1 and 4. */
        fun defaultThreadCount(): Int =
            System.getProperty(THREADS_PROPERTY)?.toIntOrNull()?.coerceAtLeast(1)
                ?: (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)
    }
}
