package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.AudioComponent

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