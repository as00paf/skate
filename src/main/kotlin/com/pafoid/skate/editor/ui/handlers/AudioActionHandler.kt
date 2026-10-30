package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.objects.EditAudioBusesCommand
import com.pafoid.skate.editor.commands.objects.EditReverbZoneCommand
import com.pafoid.skate.editor.events.AudioAction
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.core.EventSystem

class AudioActionHandler(
    eventSystem: EventSystem,
    private val projectManager: ProjectManager,
    private val undoRedoManager: UndoRedoManager,
    private val audioEngine: AudioEngine,
) {
    init {
        eventSystem.subscribe<ProjectEvent.Opened> { event ->
            audioEngine.applyConfig(event.project.gameplaySettings.audio.toConfig())
        }
        eventSystem.subscribe<AudioAction.EditReverbZone> { event ->
            val command = EditReverbZoneCommand(event.zone, event.settings)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<AudioAction.EditBuses> { event ->
            val project = projectManager.currentProject ?: return@subscribe
            val command = EditAudioBusesCommand(
                read = { project.gameplaySettings.audio },
                write = { settings ->
                    project.gameplaySettings = project.gameplaySettings.copy(audio = settings)
                    audioEngine.applyConfig(settings.toConfig())
                    projectManager.saveProject()
                },
                settings = event.settings,
            )
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
    }
}
