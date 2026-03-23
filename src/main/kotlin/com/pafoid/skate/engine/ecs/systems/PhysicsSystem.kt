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

    override fun update(dt: Float) {
        // Iterate all game objects and sync physics state
        scene.gameObjectManager.gameObjects.forEach { go ->
            val rigidBody = go.getComponent<RigidBody3D>() ?: return@forEach

            // Auto-create PhysicsComponent if RigidBody3D exists but PhysicsComponent doesn't
            // This prevents bugs where developers forget to add PhysicsComponent
            var physicsComponent = go.getComponent<PhysicsComponent>()
            if (physicsComponent == null) {
                physicsComponent = PhysicsComponent()
                go.addComponent(physicsComponent)
            }

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
