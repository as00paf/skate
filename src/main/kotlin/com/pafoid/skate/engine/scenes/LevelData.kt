package com.pafoid.skate.engine.scenes

import org.joml.Vector3f

data class LevelData(
    val gameObjects: List<GameObject>,
    val ambientLight: Vector3f,
    val lightPosition: Vector3f,
    val gravity: Vector3f
)
