package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.PrefabType
import org.joml.Vector3f

/** Spawns a catalog prefab at [position] (null: its default spot) with [material]; undo removes it. */
class SpawnPrefabCommand(
    private val prefabType: PrefabType,
    private val position: Vector3f?,
    private val material: MaterialType?,
    private val prefabsGenerator: PrefabsGenerator,
    private val gameObjectManager: GameObjectManager,
    private val scene: Scene,
) : Command {
    private var createdObject: GameObject? = null

    override fun execute() {
        val result = prefabsGenerator.spawn(prefabType, position, material)
        createdObject = result
        gameObjectManager.addGameObject(result)
        scene.isDirty = true
    }

    override fun undo() {
        createdObject?.let { obj ->
            gameObjectManager.removeGameObject(obj)
        }
    }

    override fun getDisplayName(): String = "Spawn ${prefabType.name.lowercase().replace('_', ' ')}"
    override fun getTargetName(): String? = createdObject?.name
}
