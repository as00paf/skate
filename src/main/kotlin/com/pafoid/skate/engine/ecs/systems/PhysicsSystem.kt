package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.PhysicsComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.ecs.config.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.hasComponent
import com.pafoid.skate.engine.physics3d.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.physics3d.toVector3f
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import org.joml.Vector3f

class PhysicsSystem(
    private val nativeLibraryLoader: NativeLibraryLoader,
    private val debugRenderer: DebugRenderer
) : System(priority = ExecutionPriority.EARLY) {

    private val physics3d: IPhysics3D = BulletPhysics3D(
        nativeLibraryLoader = nativeLibraryLoader,
        debugRenderer = debugRenderer
    )

    private val rigidBodies = mutableListOf<GameObject>()
    private var cacheDirty = true

    override fun init(scene: Scene) {
        super.init(scene)
        rebuildCache()
        cacheDirty = false
        scene.getComponent<ScenePhysicsComponent>()?.let { component ->
            physics3d.debugEnabled = component.debugEnabled
            physics3d.setGravity(component.gravity)
        }
    }

    override fun start() {
        cacheDirty = true
        scene.gameObjects.forEach { go ->
            physics3d.add(go)
        }
    }

    override fun invalidateCaches() {
        rigidBodies.clear()
        cacheDirty = true
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        if (cacheDirty) rebuildCache()

        // Step Bullet at deterministic fixed-step first so downstream ECS reads current frame data.
        val timeScale = scene.getComponent<TimeComponent>()?.timeScale ?: 1.0f
        physics3d.update(dt * timeScale)

        scene.getComponent<ScenePhysicsComponent>()?.let { component ->
            if (physics3d.getGravity() != component.gravity) {
                physics3d.setGravity(component.gravity)
            }
        }

        // Remove cached items no longer in scene
        val toRemove = rigidBodies.filter { !scene.gameObjects.filter { it.hasComponent<RigidBody3D>() }.contains(it) }
        toRemove.forEach { physics3d.remove(it) }
        rigidBodies.removeAll(toRemove)

        for (go in rigidBodies) {
            val rigidBody = go.getComponent<RigidBody3D>() ?: continue

            var physicsComponent = go.getComponent<PhysicsComponent>()
            if (physicsComponent == null) {
                physicsComponent = PhysicsComponent()
                go.addComponent(physicsComponent)
            }

            rigidBody.rawBody?.let { body ->
                try {
                    rigidBody.update(0f)

                    physicsComponent.updateFromPhysics(
                        body.getLinearVelocity(null).toVector3f(),
                        body.getAngularVelocity(null).toVector3f()
                    )
                } catch (e: AssertionError) {
                    // Body is not yet in physics world - skip this frame
                }
            } ?: physics3d.add(go) // Add to physics if raw body is null

            go.getComponent<PlayerController>()?.let {
                it.isGrounded = checkIfGrounded(rigidBody)
            }
        }
    }

    fun checkIfGrounded(body: RigidBody3D): Boolean {
        val originPosition = body.getWorldPosition()

        // Start the ray from the player's feet (below the collider)
        val feetY = originPosition.y
        val rayStart = Vector3f(originPosition.x, feetY, originPosition.z)

        // Ray goes down a small distance to detect ground
        val rayLength = 0.05f
        val rayEnd = Vector3f(rayStart.x, rayStart.y - rayLength, rayStart.z)

        // Exclude the player's own physics body from the raycast
        return physics3d.raycastClosest(rayStart, rayEnd, body) != null
    }

    fun toggleDebug() {
        physics3d.debugEnabled = !physics3d.debugEnabled
    }

    fun isDebugEnabled(): Boolean = physics3d.debugEnabled

    private fun rebuildCache() {
        rigidBodies.clear()

        for (go in scene.gameObjects) {
            if (go.hasComponent<RigidBody3D>()) {
                rigidBodies.add(go)
            }
        }
    }
}
