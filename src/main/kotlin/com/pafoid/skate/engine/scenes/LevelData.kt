package com.pafoid.skate.engine.scenes

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f

@Serializable
data class LevelData(
    val gameObjects: List<GameObject>,
    val sceneData: SceneData,
    @Contextual val gravity: Vector3f,
    var levelPath: String = "level.json",
    @Transient var isRunning: Boolean = false
)
