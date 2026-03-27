package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.addGameObjectToScene
import com.pafoid.skate.engine.ecs.scene.removeGameObject
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject

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
}

class ApplyTextureCommand(
    private val gameObject: GameObject,
    private val oldTexturePath: String?,
    private val newTexturePath: String
) : Command {
    override fun execute() {
        // Texture application is visual only for now - actual material system integration needed
        // This command logs the operation for potential future material system integration
    }

    override fun undo() {
        // Revert to old texture path when material system is implemented
    }
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
}

class ApplyAnimationCommand(
    private val gameObject: GameObject,
    private val animationPath: String,
    private val hadAnimator: Boolean
) : Command {
    override fun execute() {
        // Animation application - actual integration with Animator needed
        // This command logs the operation
    }

    override fun undo() {
        // Remove animation when material system is implemented
    }
}
