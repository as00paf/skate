package com.pafoid.skate.engine.physics3d.components

import com.jme3.bullet.collision.shapes.CollisionShape
import com.pafoid.skate.engine.scenes.components.Component
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class CustomCollider3D(
    @Transient val collisionShape: CollisionShape? = null
) : Component() {
    // No specific properties here, as the CollisionShape itself defines the geometry.
    // This component acts as a wrapper for arbitrary Bullet CollisionShapes.
}