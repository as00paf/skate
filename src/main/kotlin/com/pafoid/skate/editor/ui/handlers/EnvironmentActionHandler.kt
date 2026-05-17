package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.EnvironmentPropertyCommand
import com.pafoid.skate.editor.commands.EnvironmentToggleCommand
import com.pafoid.skate.editor.events.EnvironmentAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.ecs.components.TimeComponent

class EnvironmentActionHandler(
    private val undoRedoManager: UndoRedoManager,
    private val eventSystem: EventSystem,
) {
    fun init() {
        eventSystem.subscribe<EnvironmentAction.SetTimeOfDayRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayName = "Set Time of Day",
                    targetName = null,
                    setter = { t ->
                        event.timeComponent.timeOfDay = t
                        event.dayNightCycle?.cycleTime = t
                    },
                    oldValue = event.oldTime,
                    newValue = event.newTime,
                )
            )
            ensureComponentExists(event.scene.hasComponent<TimeComponent>()) {
                event.scene.addComponent(event.timeComponent)
            }
        }

        eventSystem.subscribe<EnvironmentAction.SetUseAmbientRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentToggleCommand(
                    displayName = "Toggle Use Ambient",
                    setter = { enabled -> event.lightingStateComponent.useAmbient = enabled },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
            ensureComponentExists(event.scene.hasComponent<LightingStateComponent>()) {
                event.scene.addComponent(event.lightingStateComponent)
            }
        }

        eventSystem.subscribe<EnvironmentAction.SetAutoAmbientRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentToggleCommand(
                    displayName = "Toggle Auto Ambient",
                    setter = { enabled -> event.dayNightCycle.autoAmbient = enabled },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }
    }

    private inline fun ensureComponentExists(exists: Boolean, addComponent: () -> Unit) {
        if (!exists) {
            addComponent()
        }
    }
}
