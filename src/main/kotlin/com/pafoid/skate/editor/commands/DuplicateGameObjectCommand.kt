package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform

class DuplicateGameObjectCommand(
    private val gameObject: GameObject,
    private val scene: Scene
) : Command {
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

        scene.gameObjectManager.addGameObject(duplicated)
        scene.gameObjectManager.setSelectedGameObject(duplicated)
    }

    override fun undo() {
    }

    override fun getDisplayName(): String = "Duplicate ${gameObject.name}"
    override fun getTargetName(): String? = "${gameObject.name} (Copy)"
}
