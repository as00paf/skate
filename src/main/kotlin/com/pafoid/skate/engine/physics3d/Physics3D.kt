package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.components.Collider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.physics3d.debug.PhysicsDebugger
import com.pafoid.skate.engine.physics3d.space.PhysicsSpaceManager
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.JmeVector3f
import com.pafoid.skate.engine.utils.JomlVector3f
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import org.joml.Quaternionf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class Physics3D : IPhysics3D, KoinComponent {
    private val nativeLibraryLoader: NativeLibraryLoader = NativeLibraryLoader()

    private val debugRenderer: DebugRenderer by inject()
    private val physicsDebugger: PhysicsDebugger = PhysicsDebugger(debugRenderer)

    private val physicsSpaceManager: PhysicsSpaceManager

    private var accumulator = 0f
    private val fixedTimestep = 1.0f / 60.0f

    /**
     * Toggles the rendering of debug wireframes for physics colliders.
     */
    override var debugEnabled: Boolean = false

    init {
        this.nativeLibraryLoader.loadNativeLibrary()
        val physicsSpace = PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT)
        physicsSpace.setGravity(JmeVector3f(0f, -9.81f, 0f))
        this.physicsSpaceManager = PhysicsSpaceManager(physicsSpace)
    }

    /**
     * Gets the current gravity vector of the physics world.
     *
     * @return The gravity vector in m/s².
     */
    override fun getGravity(): JomlVector3f {
        return physicsSpaceManager.getGravity()
    }

    /**
     * Sets the gravity vector for the physics world.
     *
     * @param gravity The new gravity vector in m/s².
     */
    override fun setGravity(gravity: JomlVector3f) {
        physicsSpaceManager.setGravity(gravity)
    }

    /**
     * Performs a ray test (raycast) in the physics world and returns all hits.
     *
     * @param from The start point of the ray in world space.
     * @param to The end point of the ray in world space.
     * @return A list of [PhysicsRayTestResult] containing hit information.
     */
    override fun rayTest(from: JomlVector3f, to: JomlVector3f): List<PhysicsRayTestResult> {
        return physicsSpaceManager.rayTest(from, to)
    }

    /**
     * Performs a ray test and returns only the closest hit.
     *
     * @param from The start point of the ray.
     * @param to The end point of the ray.
     * @return The closest [PhysicsRayTestResult], or null if no hit occurred.
     */
    override fun raycastClosest(from: JomlVector3f, to: JomlVector3f): PhysicsRayTestResult? {
        val results = rayTest(from, to)
        return if (results.isEmpty()) null else results.minByOrNull { it.hitFraction }
    }

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
            if (rb.rawBody != null) {
                if (rb.rawBody?.mass != desiredMass) {
                    physicsSpaceManager.remove(go)
                    rb.rawBody = null
                } else {
                    // Body exists and mass is correct, just sync properties
                    update(go)
                    return
                }
            }

            if (rb.rawBody == null) {
                val colliders = go.components.filterIsInstance<Collider3D>()
                val compound = CompoundCollisionShape()

                colliders.forEach { c ->
                    val shape = c.createShape()
                    compound.addChildShape(shape, JmeVector3f(c.offset.x, c.offset.y, c.offset.z))
                }

                // If no colliders, provide a default box
                if (colliders.isEmpty()) {
                    val shape = BoxCollisionShape(JmeVector3f(1f, 1f, 1f))
                    shape.margin = 0.04f
                    compound.addChildShape(shape, JmeVector3f(0f, 0f, 0f))
                }

                val body = PhysicsRigidBody(compound, desiredMass)
                rb.rawBody = body
                update(go) // Initial property sync

                if (rb.bodyType == BodyType.Kinematic) {
                    body.isKinematic = true
                }

                if (rb.useCCD) {
                    body.setCcdMotionThreshold(0.1f)
                    body.setCcdSweptSphereRadius(0.1f)
                }

                physicsSpaceManager.add(go)
            }
        }
    }

    /**
     * Updates the physics properties of a GameObject's rigid body based on its component state.
     * This is typically used to sync changes from the editor or game logic to the physics engine.
     *
     * @param go The GameObject to update.
     */
    override fun update(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>()
        val body = rb?.rawBody ?: return
        syncBodyProperties(body, rb, go)
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
        body.collisionShape.setScale(JmeVector3f(scale.x, scale.y, scale.z))
        body.friction = rb.friction
        body.setDamping(rb.linearDamping, rb.angularDamping)

        val q = Quaternionf().rotationXYZ(
            Math.toRadians(rot.x.toDouble()).toFloat(),
            Math.toRadians(rot.y.toDouble()).toFloat(),
            Math.toRadians(rot.z.toDouble()).toFloat()
        )
        body.setPhysicsRotation(Quaternion(q.x, q.y, q.z, q.w))
    }

    /**
     * Removes a GameObject from the physics simulation.
     * It destroys the associated Bullet rigid body.
     *
     * @param go The GameObject to remove.
     */
    override fun remove(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>()
        rb?.rawBody?.let {
            physicsSpaceManager.remove(go)
            rb.rawBody = null
        }
    }

    /**
     * Steps the physics simulation forward by the given delta time.
     * It uses a fixed timestep accumulator to ensure deterministic physics behavior
     * regardless of the rendering frame rate.
     *
     * @param dt The time elapsed since the last frame in seconds.
     */
    override fun update(dt: Float) {
        accumulator += dt
        while (accumulator >= fixedTimestep) {
            val startTime = System.nanoTime()
            physicsSpaceManager.update(fixedTimestep) // This will internally step the physics
            val endTime = System.nanoTime()
            EngineStats.physicsStepTime.set(endTime - startTime)
            accumulator -= fixedTimestep
        }

        if (debugEnabled) {
            physicsDebugger.drawDebugWireframes(physicsSpaceManager.getPhysicsSpace())
        }
    }
    

    override fun destroy() {
        physicsSpaceManager.destroy()
    }
}
