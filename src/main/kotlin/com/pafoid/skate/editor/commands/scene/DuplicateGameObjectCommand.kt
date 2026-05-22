package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.getComponent

class DuplicateGameObjectCommand(
    private val gameObject: GameObject,
    private val scene: Scene,
    private val gameObjectManager: GameObjectManager,
) : ExecuteOnlyCommand {
    override fun execute() {
        val duplicated = GameObject("${gameObject.name} (Copy)")
        val originalTransform = gameObject.getComponent<Transform>()
        val newTransform = Transform()
        originalTransform?.let { orig ->
            newTransform.copyFrom(orig)
        }
        newTransform.translation.add(1f, 0f, 0f)
        duplicated.addComponent(newTransform)

        val originalRender = gameObject.getComponent<RenderComponent>()
        if (originalRender != null) {
            val newRender = RenderComponent(
                modelGuid = originalRender.modelGuid,
                albedoTextureGuid = originalRender.albedoTextureGuid
            )
            duplicated.addComponent(newRender)
        }

        gameObjectManager.addGameObject(duplicated)
        scene.selectedGameObject = duplicated
    }

    override fun undo() {
    }

    override fun getDisplayName(): String = "Duplicate ${gameObject.name}"
    override fun getTargetName(): String? = "${gameObject.name} (Copy)"
}
