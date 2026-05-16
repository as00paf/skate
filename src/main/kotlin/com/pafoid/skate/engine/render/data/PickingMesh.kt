package com.pafoid.skate.engine.render.data

import org.joml.Matrix4f
import org.joml.Vector3f

data class PickingMesh(
    val vertices: List<Vector3f>,
    val transform: Matrix4f,
    val objectId: Int
)