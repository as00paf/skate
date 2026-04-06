package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.config.DirectionalLightConfig
import com.pafoid.skate.engine.ecs.scene.addGameObjectToScene
import com.pafoid.skate.engine.ecs.scene.removeGameObject
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject
import com.pafoid.skate.engine.events.AnimationApplied
import com.pafoid.skate.engine.events.AnimationRemoved
import com.pafoid.skate.engine.events.EventSystem
import com.pafoid.skate.engine.events.GameEvent
import com.pafoid.skate.engine.events.TextureApplied
import org.joml.Vector3f

class TransformCommand(
    private val gameObject: GameObject,
    oldTransform: Transform,
    newTransform: Transform
) : Command {
    private val oldT = Transform().apply { copyFrom(oldTransform) }
    private val newT = Transform().apply { copyFrom(newTransform) }

    override fun execute() {
        gameObject.getComponent<Transform>()?.copyFrom(newT)
    }

    override fun undo() {
        gameObject.getComponent<Transform>()?.copyFrom(oldT)
    }

    override fun getDisplayName(): String = "Transform"
    override fun getTargetName(): String? = gameObject.name
}

class CreateGameObjectCommand(
    private val gameObject: GameObject,
    private val scene: Scene,
) : Command {
    override fun execute() {
        scene.addGameObjectToScene(gameObject)
        scene.setSelectedGameObject(gameObject)
    }

    override fun undo() {
        scene.removeGameObject(gameObject)
        scene.setSelectedGameObject(null)
    }

    override fun getDisplayName(): String = "Create GameObject"
    override fun getTargetName(): String? = gameObject.name
}

class DeleteGameObjectCommand(
    private val gameObject: GameObject,
    private val scene: Scene,
) : Command {
    override fun execute() {
        scene.removeGameObject(gameObject)
        scene.setSelectedGameObject(null)
    }

    override fun undo() {
        scene.addGameObjectToScene(gameObject)
        scene.setSelectedGameObject(gameObject)
    }

    override fun getDisplayName(): String = "Delete GameObject"
    override fun getTargetName(): String? = gameObject.name
}

class ApplyTextureCommand(
    private val gameObject: GameObject,
    private val oldTexturePath: String?,
    private val newTexturePath: String,
    private val resourceManager: ResourceManager,
    private val eventSystem: EventSystem
) : Command {
    override fun execute() {
        val renderComponent = gameObject.getComponent<RenderComponent>()
        renderComponent?.let { component ->
            val texture = resourceManager.loadTextureSync(newTexturePath)
            val meshPart = component.model.mesh[0]
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
        eventSystem.publish(TextureApplied(gameObject, newTexturePath))
    }

    override fun undo() {
        // Restore old texture
        if (oldTexturePath != null) {
            val renderComponent = gameObject.getComponent<RenderComponent>()
            renderComponent?.let { component ->
                val texture = resourceManager.loadTextureSync(oldTexturePath)
                val meshPart = component.model.mesh[0]
                val newMaterial = Material(baseColorTexture = texture)
                val newMeshPart = MeshPart(meshPart.rawModel, newMaterial, meshPart.inverseBindMatrices)
                val newModel = TexturedModel(listOf(newMeshPart))
                
                val newRenderComponent = RenderComponent(
                    newModel,
                    component.shininess,
                    component.reflectivity,
                    component.textureScale,
                    component.renderMode,
                    component.castShadow,
                    component.receiveShadow
                )
                
                gameObject.removeComponent<RenderComponent>()
                gameObject.addComponent(newRenderComponent)
            }
            eventSystem.publish(TextureApplied(gameObject, oldTexturePath))
        }
    }

    override fun getDisplayName(): String = "Apply Texture"
    override fun getTargetName(): String? = gameObject.name
}

class AddAudioComponentCommand(
    private val gameObject: GameObject,
    private val soundPath: String,
    private val hadAudioComponent: Boolean
) : Command {
    private var createdComponent: AudioComponent? = null

    override fun execute() {
        var audioComponent = gameObject.getComponent<AudioComponent>()
        if (audioComponent == null) {
            audioComponent = AudioComponent()
            gameObject.addComponent(audioComponent)
            createdComponent = audioComponent
        }
        audioComponent.soundFilePath = soundPath
    }

    override fun undo() {
        if (!hadAudioComponent && createdComponent != null) {
            gameObject.components.removeIf { it == createdComponent }
        } else {
            val audioComponent = gameObject.getComponent<AudioComponent>()
            audioComponent?.let { it.soundFilePath = "" }
        }
    }

    override fun getDisplayName(): String = "Add Audio Component"
    override fun getTargetName(): String? = gameObject.name
}

class ApplyAnimationCommand(
    private val gameObject: GameObject,
    private val oldAnimationPath: String?,
    private val newAnimationPath: String,
    private val resourceManager: ResourceManager,
    private val eventSystem: EventSystem
) : Command {
    override fun execute() {
        val animator = gameObject.getComponent<Animator>()
        animator?.let { anim ->
            val animation = resourceManager.getAnimation(newAnimationPath)
            animation?.let { 
                anim.addAnimation(it)
                eventSystem.publish(AnimationApplied(gameObject, newAnimationPath))
            }
        }
    }

    override fun undo() {
        // Animation undo is complex - for now we just log
        // A full implementation would require tracking which animation was added
        eventSystem.publish(AnimationRemoved(gameObject, newAnimationPath))
    }

    override fun getDisplayName(): String = "Apply Animation"
    override fun getTargetName(): String? = gameObject.name
}

/**
 * Command for changing a single environment property (time of day, light config values, etc.).
 */
class EnvironmentPropertyCommand<T>(
    private val displayName: String,
    private val targetName: String? = null,
    private val setter: (T) -> Unit,
    private val oldValue: T,
    private val newValue: T
) : Command {
    override fun execute() {
        setter(newValue)
    }

    override fun undo() {
        setter(oldValue)
    }

    override fun getDisplayName(): String = displayName
    override fun getTargetName(): String? = targetName
}

/**
 * Command for toggling a boolean environment property (checkboxes).
 */
class EnvironmentToggleCommand(
    private val displayName: String,
    private val targetName: String? = null,
    private val setter: (Boolean) -> Unit,
    private val oldValue: Boolean,
    private val newValue: Boolean
) : Command {
    override fun execute() {
        setter(newValue)
    }

    override fun undo() {
        setter(oldValue)
    }

    override fun getDisplayName(): String = displayName
    override fun getTargetName(): String? = targetName
}
