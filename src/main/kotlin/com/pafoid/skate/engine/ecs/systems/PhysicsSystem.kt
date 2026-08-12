package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.hasComponent
import com.pafoid.skate.engine.physics3d.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import org.joml.Vector3f

class PhysicsSystem(
    private val debugRenderer: DebugRenderer
) : System(priority = ExecutionPriority.EARLY) {

    private val physics3d: IPhysics3D = BulletPhysics3D(
        debugRenderer = debugRenderer
    )

    private val cache = mutableListOf<GameObject>()

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
        scene.children.forEach { go ->
            physics3d.add(go)
        }
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        if (cacheDirty) rebuildCache()

        // Step Bullet at deterministic fixed-step first so downstream ECS reads current frame data.
        val timeScale = scene.getComponent<DayNightCycleComponent>()?.timeScale ?: 1.0f
        physics3d.update(dt * timeScale)

        scene.getComponent<ScenePhysicsComponent>()?.let { component ->
            if (physics3d.getGravity() != component.gravity) {
                physics3d.setGravity(component.gravity)
            }
        }

        // Remove cached items no longer in scene
        val toRemove = cache.filter { !scene.children.filter { it.hasComponent<RigidBody3D>() }.contains(it) }
        toRemove.forEach { physics3d.remove(it) }
        cache.removeAll(toRemove)

        for (go in cache) {
            val rigidBody = go.getComponent<RigidBody3D>() ?: continue

            rigidBody.rawBody?.let {
                try {
                    rigidBody.update(dt)
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

    override fun invalidateCache() {
        cache.clear()
        cacheDirty = true
    }

    override fun rebuildCache() {
        cache.clear()
        for (go in scene.children) {
            if (go.hasComponent<RigidBody3D>()) {
                cache.add(go)
            }
        }
    }
}
