package com.pafoid.skate.game.level

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.scene.SceneData
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Serializable level data for save/load operations.
 *
 * Scene state is stored in components on the Scene GameObject:
 * - [EnvironmentComponent] for sky/fog settings
 * - [TimeComponent] for time of day and time scale
 * - [LightingStateComponent] for ambient lighting
 * - [LightingComponent] for computed lighting state
 *
 * @property gameObjects List of GameObjects in the level
 * @property sceneData Minimal scene configuration (light, sun, levelPath)
 * @property levelPath Path to the level file
 */
@Serializable
data class LevelData(
    val gameObjects: List<GameObject>,
    val sceneData: SceneData,
    var levelPath: String = "level.json",
    @Transient var isRunning: Boolean = false
)
