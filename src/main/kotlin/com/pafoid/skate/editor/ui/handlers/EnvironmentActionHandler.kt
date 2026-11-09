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
                    displayNameKey = "command.environment.set_time_of_day",
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
                    displayNameKey = "command.environment.toggle_auto_ambient",
                    setter = { enabled -> event.dayNightCycle.autoAmbient = enabled },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetSunDirectionRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayNameKey = "command.environment.set_sun_direction",
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
                    displayNameKey = "command.environment.set_sun_color",
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
                    displayNameKey = "command.environment.set_sun_intensity",
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
                    displayNameKey = "command.environment.set_shadow_distance",
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
                    displayNameKey = "command.environment.toggle_auto_calculate_bounds",
                    setter = { enabled -> event.lightConfig.autoCalculateBounds = enabled },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetStabilizeProjectionRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentToggleCommand(
                    displayNameKey = "command.environment.toggle_stabilize_projection",
                    setter = { enabled -> event.lightConfig.stabilizeProjection = enabled },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetDepthBiasRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayNameKey = "command.environment.set_depth_bias",
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
                    displayNameKey = "command.environment.set_slope_scaled_bias",
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
                    displayNameKey = "command.environment.set_ambient_light",
                    targetName = null,
                    setter = { ambient: Vector3f ->
                        event.environment.ambientColor.set(ambient)
                    },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }

        eventSystem.subscribe<EnvironmentAction.SetAmbientIntensityRequested> { event ->
            undoRedoManager.executeCommand(
                EnvironmentPropertyCommand(
                    displayNameKey = "command.environment.set_ambient_intensity",
                    targetName = null,
                    setter = { intensity: Float ->
                        event.environment.ambientIntensity = intensity
                    },
                    oldValue = event.oldValue,
                    newValue = event.newValue,
                )
            )
        }
    }
}
