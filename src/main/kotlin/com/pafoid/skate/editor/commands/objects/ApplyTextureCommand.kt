package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.removeComponent

class ApplyTextureCommand(
    private val gameObject: GameObject,
    private val newTexturePath: String,
    private val assetsManager: AssetsManager,
    private val eventSystem: EventSystem
) : ExecuteOnlyCommand {
    override fun execute() {
        val renderComponent = gameObject.getComponent<RenderComponent>()
        renderComponent?.let { component ->
            val oldModel = component.model ?: return@let
            val texture = assetsManager.getTexture(newTexturePath)
            val meshPart = oldModel.mesh[0]
            val newMaterial = Material(baseColorTexture = texture)
            val newMeshPart = MeshPart(
                rawModel = meshPart.rawModel,
                material = newMaterial,
                inverseBindMatrices = meshPart.inverseBindMatrices
            )
            val newModel = TexturedModel(mesh = listOf(newMeshPart))

            // Create new RenderComponent with updated model
            val newRenderComponent = RenderComponent(
                modelPath = "",
                model = newModel,
                albedoTextureGuid = "",
                normalMapGuid = "",
                metallicRoughnessGuid = "",
                shininess = component.shininess,
                reflectivity = component.reflectivity,
                textureScale = component.textureScale,
                renderMode = component.renderMode,
                castShadow = component.castShadow,
                receiveShadow = component.receiveShadow
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

