package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.collision.shapes.CylinderCollisionShape
import com.jme3.bullet.collision.shapes.HullCollisionShape
import com.jme3.bullet.collision.shapes.MeshCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.components.Collider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.JmeVector3f
import com.pafoid.skate.engine.utils.JomlVector3f
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import org.joml.Quaternionf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.component.get

class Physics3D : IPhysics3D, KoinComponent {
    private val debugRenderer: DebugRenderer by inject()
    private val nativeLibraryLoader: NativeLibraryLoader
    private val physicsSpace: PhysicsSpace

    /**
     * Toggles the rendering of debug wireframes for physics colliders.
     */
    override var debugEnabled = false

    init {
        // Attempt to get the NativeLibraryLoader from Koin, otherwise create a new one
        this.nativeLibraryLoader = try {
            get()
        } catch (e: Exception) {
            // For cases where Koin isn't properly set up (e.g., in tests)
            NativeLibraryLoader()
        }
        this.nativeLibraryLoader.loadNativeLibrary()
        this.physicsSpace = PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT)
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

    /**
     * Performs a ray test (raycast) in the physics world and returns all hits.
     *
     * @param from The start point of the ray in world space.
     * @param to The end point of the ray in world space.
     * @return A list of [PhysicsRayTestResult] containing hit information.
     */
    override fun rayTest(from: JomlVector3f, to: JomlVector3f): List<PhysicsRayTestResult> {
        val start = JmeVector3f(from.x, from.y, from.z)
        val end = JmeVector3f(to.x, to.y, to.z)
        return physicsSpace.rayTest(start, end)
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
                    physicsSpace.remove(rb.rawBody)
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
                
                physicsSpace.add(body)
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
            physicsSpace.remove(it)
            rb.rawBody = null
        }
    }

    private var accumulator = 0f
    private val fixedTimestep = 1.0f / 60.0f

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
            physicsSpace.update(fixedTimestep, 0)
            val endTime = System.nanoTime()
            EngineStats.physicsStepTime.set(endTime - startTime)
            accumulator -= fixedTimestep
        }

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

        when (axis) {
            0 -> { // X-axis is height
                radius = halfExtents.y
                height = halfExtents.x * 2f
            }

            1 -> { // Y-axis is height
                radius = halfExtents.x
                height = halfExtents.y * 2f
            }

            2 -> { // Z-axis is height
                radius = halfExtents.x
                height = halfExtents.z * 2f
            }

            else -> {
                radius = halfExtents.x
                height = halfExtents.y * 2f
            }
        }
        debugRenderer.addCylinder3D(pos, rot, radius, height, axis, color)
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
        debugRenderer.addBox3D(pos, rot, JomlVector3f(halfExtents.x, halfExtents.y, halfExtents.z), color)
    }

    override fun destroy() {
        physicsSpace.destroy()
    }
}
