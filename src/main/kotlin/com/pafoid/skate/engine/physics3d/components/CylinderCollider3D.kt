package com.pafoid.skate.engine.physics3d.components

import com.pafoid.skate.engine.scenes.components.Component
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
class CylinderCollider3D(
    var radius: Float = 0.5f,
    var height: Float = 1.0f,
    var axis: Int = 1 // 0=X, 1=Y, 2=Z
) : Component() {
    @Contextual val offset: Vector3f = Vector3f()
    var margin: Float = 0.04f
}