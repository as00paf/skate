package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RenderComponent

class ApplyTextureCommand(
    private val gameObject: GameObject,
    private val newTexturePath: String,
    private val resourceManager: ResourceManager,
    private val eventSystem: EventSystem
) : ExecuteOnlyCommand {
    override fun execute() {
        val renderComponent = gameObject.getComponent<RenderComponent>()
        renderComponent?.let { component ->
            val oldModel = component.model ?: return@let
            val texture = resourceManager.loadTextureSync(newTexturePath)
            val meshPart = oldModel.mesh[0]
            val newMaterial = Material(baseColorTexture = texture)
            val newMeshPart = MeshPart(meshPart.rawModel, newMaterial, meshPart.inverseBindMatrices)
            val newModel = TexturedModel(listOf(newMeshPart))

            // Create new RenderComponent with updated model
            val newRenderComponent = RenderComponent(
                newModel,
                component.shininess,
                component.reflectivity,
                component.textureScale,
                component.renderMode,
                component.castShadow,
                component.receiveShadow
            )

            // Replace component on game object
            gameObject.removeComponent<RenderComponent>()
            gameObject.addComponent(newRenderComponent)
        }
        // Publish event for UI update
        eventSystem.publish(ViewportAction.TextureApplied(gameObject, newTexturePath))
    }

    override fun undo() {
        // Execute-only: restoring prior material stack is not reliably supported yet.
    }

    override fun getDisplayName(): String = "Apply Texture"
    override fun getTargetName(): String? = gameObject.name
}
