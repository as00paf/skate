package com.pafoid.skate.engine.physics3d.components

import com.pafoid.skate.engine.scenes.components.Component
import org.joml.Vector3f

class BoxCollider3D(val halfExtents: Vector3f = Vector3f(1f, 1f, 1f)) : Component() {
    val offset: Vector3f = Vector3f()
}