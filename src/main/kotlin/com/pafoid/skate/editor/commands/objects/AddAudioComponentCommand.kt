package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.getComponent

class AddAudioComponentCommand(
    private val gameObject: GameObject,
    private val soundPath: String
) : Command {
    private var createdComponent: AudioComponent? = null
    private var oldSoundPath: String = ""
    private var hadAudioComponent: Boolean = false

    override fun execute() {
        var audioComponent = gameObject.getComponent<AudioComponent>()
        hadAudioComponent = audioComponent != null
        oldSoundPath = audioComponent?.soundFilePath ?: ""
        if (audioComponent == null) {
            audioComponent = AudioComponent()
            gameObject.addComponent(audioComponent)
            createdComponent = audioComponent
        }
        audioComponent.soundFilePath = soundPath
    }

    override fun undo() {
        if (!hadAudioComponent) {
            val created = createdComponent ?: return
            gameObject.components.removeIf { it == created }
        } else {
            val audioComponent = gameObject.getComponent<AudioComponent>()
            audioComponent?.let { it.soundFilePath = oldSoundPath }
        }
    }

    override fun getDisplayName(): String = "Add Audio Component"
    override fun getTargetName(): String? = gameObject.name
}
