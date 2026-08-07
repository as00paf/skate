package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.EnvironmentPropertyCommand
import com.pafoid.skate.editor.commands.EnvironmentToggleCommand
import com.pafoid.skate.editor.events.EnvironmentAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import org.joml.Vector3f

class EnvironmentActionHandler(
    private val undoRedoManager: UndoRedoManager,
    private val eventSystem: EventSystem,
) {
    init {
        eventSystem.subscribe<EnvironmentAction.SetTimeOfDayRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayName = "Set Time of Day",
                    targetName = null,
                    setter = { t ->
                        event.dayNightCycle?.timeOfDay = t
                    },
                    oldValue = event.oldTime,
                    newValue = event.newTime,
                )
            )
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

        eventSystem.subscribe<EnvironmentAction.SetSunDirectionRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayName = "Set Sun Direction",
                    targetName = null,
                    setter = { direction: Vector3f ->
                        event.lightConfig.direction.set(direction).normalize()
                    },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetSunColorRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayName = "Set Sun Color",
                    targetName = null,
                    setter = { color: Vector3f ->
                        event.lightConfig.color.set(color)
                    },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetSunIntensityRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayName = "Set Sun Intensity",
                    targetName = null,
                    setter = { intensity: Float ->
                        event.lightConfig.intensity = intensity
                    },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetShadowDistanceRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayName = "Set Shadow Distance",
                    targetName = null,
                    setter = { distance: Float ->
                        event.lightConfig.shadowDistance = distance
                    },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetAutoCalculateBoundsRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentToggleCommand(
                    displayName = "Toggle Auto Calculate Bounds",
                    setter = { enabled -> event.lightConfig.autoCalculateBounds = enabled },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetStabilizeProjectionRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentToggleCommand(
                    displayName = "Toggle Stabilize Projection",
                    setter = { enabled -> event.lightConfig.stabilizeProjection = enabled },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetDepthBiasRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayName = "Set Depth Bias",
                    targetName = null,
                    setter = { depthBias: Float ->
                        event.lightConfig.depthBias = depthBias
                    },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetSlopeScaledBiasRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayName = "Set Slope Scaled Bias",
                    targetName = null,
                    setter = { slopeBias: Float ->
                        event.lightConfig.slopeScaledBias = slopeBias
                    },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetAmbientLightRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayName = "Set Ambient Light",
                    targetName = null,
                    setter = { ambient: Vector3f ->
                        event.ambientLightComponent.lightColor.set(ambient)
                    },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetAmbientIntensityRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayName = "Set Ambient Intensity",
                    targetName = null,
                    setter = { intensity: Float ->
                        event.ambientLightComponent.intensity = intensity
                    },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }
    }
}
