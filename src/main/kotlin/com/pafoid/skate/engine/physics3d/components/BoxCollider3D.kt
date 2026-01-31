package com.pafoid.skate.engine.physics3d.components

import com.pafoid.skate.engine.scenes.components.Component
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
class BoxCollider3D(@Contextual val halfExtents: Vector3f = Vector3f(1f, 1f, 1f)) : Component() {
    @Contextual val offset: Vector3f = Vector3f()
    var margin: Float = 0.04f
}