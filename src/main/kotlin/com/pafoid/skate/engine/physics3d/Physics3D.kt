package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.adapter.GameObjectPhysicsAdapter
import com.pafoid.skate.engine.physics3d.components.Collider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.physics3d.debug.PhysicsDebugger
import com.pafoid.skate.engine.physics3d.space.PhysicsSpaceManager
import com.pafoid.skate.engine.physics3d.stepper.PhysicsStepper
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.JmeVector3f
import com.pafoid.skate.engine.utils.JomlVector3f
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class Physics3D : IPhysics3D, KoinComponent {
    private val nativeLibraryLoader: NativeLibraryLoader = NativeLibraryLoader()

    private val debugRenderer: DebugRenderer by inject()
    private val physicsDebugger: PhysicsDebugger = PhysicsDebugger(debugRenderer)

    private val physicsSpaceManager: PhysicsSpaceManager
    private val physicsStepper: PhysicsStepper
    private val gameObjectPhysicsAdapter: GameObjectPhysicsAdapter

    /**
     * Toggles the rendering of debug wireframes for physics colliders.
     */
    override var debugEnabled: Boolean = false

    init {
        this.nativeLibraryLoader.loadNativeLibrary()
        val physicsSpace = PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT)
        physicsSpace.setGravity(JmeVector3f(0f, -9.81f, 0f))
        this.physicsSpaceManager = PhysicsSpaceManager(physicsSpace)
        this.physicsStepper = PhysicsStepper(physicsSpace)
        this.gameObjectPhysicsAdapter = GameObjectPhysicsAdapter()
    }

    /**
     * Steps the physics simulation forward by the given delta time.
     * It uses a fixed timestep accumulator to ensure deterministic physics behavior
     * regardless of the rendering frame rate.
     *
     * @param dt The time elapsed since the last frame in seconds.
     */
    override fun update(dt: Float) {
        physicsStepper.stepPhysics(dt)

        if (debugEnabled) {
            physicsDebugger.drawDebugWireframes(physicsSpaceManager.getPhysicsSpace())
        }
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
        gameObjectPhysicsAdapter.add(go, physicsSpaceManager.getPhysicsSpace())
    }

    /**
     * Updates the physics properties of a GameObject's rigid body based on its component state.
     * This is typically used to sync changes from the editor or game logic to the physics engine.
     *
     * @param go The GameObject to update.
     */
    override fun update(go: GameObject) {
        gameObjectPhysicsAdapter.update(go, physicsSpaceManager.getPhysicsSpace())
    }

    /**
     * Removes a GameObject from the physics simulation.
     * It destroys the associated Bullet rigid body.
     *
     * @param go The GameObject to remove.
     */
    override fun remove(go: GameObject) {
        gameObjectPhysicsAdapter.remove(go, physicsSpaceManager.getPhysicsSpace())
    }

    override fun destroy() {
        physicsSpaceManager.destroy()
    }
}
