package com.pafoid.skate.engine.ecs.scene

import com.pafoid.skate.engine.render.data.DirectionalLight
import com.pafoid.skate.engine.render.data.Light
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * Scene-wide data and settings for serialization.
 *
 * This class holds minimal serializable scene configuration that persists across saves.
 * Most scene state is now stored in components on the Scene GameObject:
 * - [EnvironmentComponent] for sky/fog settings
 * - [TimeComponent] for timeOfDay and timeScale
 * - [LightingStateComponent] for ambientLight and useAmbient
 *
 * @property light Ambient point light at camera position (legacy)
 * @property sun Directional light configuration
 * @property levelPath Path to the level save file
 */
@Serializable
data class SceneData(
    @Contextual var light: Light = Light(Vector3f(0f, 0f, 20f)),
    var sun: DirectionalLight = DirectionalLight(),
    var levelPath: String = "level.json",
)