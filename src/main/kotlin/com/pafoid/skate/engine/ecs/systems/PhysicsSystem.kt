package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.PhysicsComponent
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.physics3d.toVector3f

/**
 * System responsible for syncing physics state to PhysicsComponent.
 *
 * This system runs at [ExecutionPriority.EARLY] to ensure physics state is ready before
 * gameplay systems like [TrickDetector] and [PlayerController] read from [PhysicsComponent].
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
 * // PhysicsComponent on GameObjects will be updated automatically
 * val physics = gameObject.getComponent<PhysicsComponent>()
 * val speed = physics?.speed ?: 0f
 * ```
 */
class PhysicsSystem : System(priority = ExecutionPriority.EARLY) {

    override fun update(dt: Float) {
        // Iterate all game objects and sync physics state
        scene.gameObjectManager.gameObjects.forEach { go ->
            val physicsComponent = go.getComponent<PhysicsComponent>() ?: return@forEach
            val rigidBody = go.getComponent<RigidBody3D>() ?: return@forEach

            // Sync physics state from body to component
            rigidBody.rawBody?.let { body ->
                physicsComponent.updateFromPhysics(
                    body.getLinearVelocity(null).toVector3f(),
                    body.getAngularVelocity(null).toVector3f()
                )
            }
        }
    }

    override fun editorUpdate(dt: Float) {
        // Also update in editor mode for debug visualization
        update(dt)
    }
}
