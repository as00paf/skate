package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabType
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import org.joml.Vector3f

class SpawnPrefabCommand(
    private val prefabType: PrefabType,
    private val position: Vector3f?,
    private val prefabsGenerator: PrefabsGenerator,
    private val gameObjectManager: GameObjectManager,
) : Command {
    private var createdObject: GameObject? = null

    override fun execute() {
        val defaultPosition = when (prefabType) {
            PrefabType.RAIL -> Vector3f(0f, 0.5f, 0f)
            PrefabType.LEDGE -> Vector3f(0f, 0.25f, 0f)
            PrefabType.KICKER -> Vector3f(0f, 0f, 0f)
            PrefabType.MANUAL_PAD -> Vector3f(0f, 0.1f, 0f)
            PrefabType.BANK -> Vector3f(0f, 0f, 0f)
            PrefabType.QUARTER_PIPE -> Vector3f(0f, 0f, 0f)
            else -> Vector3f(0f, 0f, 0f)
        }
        val pos = position ?: defaultPosition

        createdObject = when (prefabType) {
            PrefabType.RAIL -> prefabsGenerator.spawnRail(pos, null)
            PrefabType.LEDGE -> prefabsGenerator.spawnLedge(pos, null)
            PrefabType.KICKER -> prefabsGenerator.spawnKicker(pos, null)
            PrefabType.MANUAL_PAD -> prefabsGenerator.spawnManualPad(pos, null)
            PrefabType.BANK -> prefabsGenerator.spawnBank(pos, null)
            PrefabType.QUARTER_PIPE -> prefabsGenerator.spawnQuarterPipe(pos, null)
            else -> null
        }
    }

    override fun undo() {
        createdObject?.let { obj ->
            gameObjectManager.removeGameObject(obj)
        }
    }

    override fun getDisplayName(): String = "Spawn ${prefabType.name.lowercase().replace('_', ' ')}"
    override fun getTargetName(): String? = createdObject?.name
}