package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.attachComponent
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.removeComponent

/** Replaces the object's RenderComponent with one using [newTexturePath]; undo puts the previous one back (EDT-10). */
class ApplyTextureCommand(
    private val gameObject: GameObject,
    private val newTexturePath: String,
    private val assetsManager: AssetsManager,
    private val eventSystem: EventSystem
) : Command {
    private var previous: RenderComponent? = null
    private var applied: RenderComponent? = null

    override fun execute() {
        applied?.let { done ->
            previous?.let { gameObject.removeComponent(it) }
            gameObject.attachComponent(done)
            eventSystem.publish(ViewportAction.TextureApplied(gameObject, newTexturePath))
            return
        }
        val renderComponent = gameObject.getComponent<RenderComponent>()
        renderComponent?.let { component ->
            val texture = assetsManager.getTexture(newTexturePath)
            val newMaterial = Material(baseColorTexture = texture)
            val newMeshPart = MeshPart(
                material = newMaterial,
            )
            val newModel = `3dModel`(mesh = listOf(newMeshPart))

            // Create new RenderComponent with updated model
            val newRenderComponent = RenderComponent(
                model = newModel,
                textureScale = component.textureScale,
                renderMode = component.renderMode,
                castShadow = component.castShadow,
                receiveShadow = component.receiveShadow
            )

            // Replace component on game object
            gameObject.removeComponent<RenderComponent>()
            gameObject.addComponent(newRenderComponent)
            previous = component
            applied = newRenderComponent
        }
        // Publish event for UI update
        eventSystem.publish(ViewportAction.TextureApplied(gameObject, newTexturePath))
    }

    override fun undo() {
        val done = applied ?: return
        gameObject.removeComponent(done)
        previous?.let { gameObject.attachComponent(it) }
    }

    override fun getDisplayName(): String = "Apply Texture"
    override fun getTargetName(): String? = gameObject.name
}

