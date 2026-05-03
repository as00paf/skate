package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.PhysicsComponent
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.physics3d.toVector3f

/**
 * System responsible for syncing physics state to PhysicsComponent.
 *
 * This system runs at [ExecutionPriority.EARLY] to ensure physics state is ready before
 * gameplay systems like [TrickDetector] and [PlayerController] read from [PhysicsComponent].
 *
 * Auto-creates PhysicsComponent on GameObjects that have RigidBody3D but no PhysicsComponent.
 * This prevents bugs where developers forget to add PhysicsComponent to physics-enabled objects.
 *
 * Note: Landing/Takeoff events are published by SkateboardPhysics which has accurate
 * grounded detection via raycast suspension. This system focuses on syncing physics state.
 *
 * ## Usage
 *
 * ```kotlin
 * val physicsSystem = PhysicsSystem()
 * scene.addSystem(physicsSystem)
 *
 * // PhysicsComponent will be auto-created on GameObjects with RigidBody3D
 * val physics = gameObject.getComponent<PhysicsComponent>()
 * val speed = physics?.speed ?: 0f
 * ```
 */
class PhysicsSystem : System(priority = ExecutionPriority.EARLY) {

    private val rigidBodies = mutableListOf<GameObject>()
    private var cacheDirty = true

    override fun init(scene: Scene) {
        super.init(scene)
        rebuildCache()
        cacheDirty = false
    }

    override fun start() {
        cacheDirty = true
    }

    override fun invalidateCaches() {
        rigidBodies.clear()
        cacheDirty = true
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        if (cacheDirty) rebuildCache()

        for (go in rigidBodies) {
            val rigidBody = go.getComponent<RigidBody3D>() ?: continue

            var physicsComponent = go.getComponent<PhysicsComponent>()
            if (physicsComponent == null) {
                physicsComponent = PhysicsComponent()
                go.addComponent(physicsComponent)
            }

            rigidBody.rawBody?.let { body ->
                try {
                    physicsComponent.updateFromPhysics(
                        body.getLinearVelocity(null).toVector3f(),
                        body.getAngularVelocity(null).toVector3f()
                    )
                } catch (e: AssertionError) {
                    // Body is not yet in physics world - skip this frame
                }
            }
        }
    }

    private fun rebuildCache() {
        rigidBodies.clear()

        for (go in scene.gameObjects) {
            if (go.hasComponent<RigidBody3D>()) {
                rigidBodies.add(go)
            }
        }
    }
}
