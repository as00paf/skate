package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.render.CameraComponent
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.UnitSystem
import com.pafoid.skate.engine.utils.UnitType
import com.pafoid.skate.engine.utils.Units
import imgui.ImGui
import imgui.flag.ImGuiKey
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.abs

class MeasureTool(
    inputProvider: InputProvider,
    undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
    private val settingsManager: EditorSettingsManager,
) : Gizmo(inputProvider, undoRedoManager) {
    private var startPoint: Vector3f? = null
    private var endPoint: Vector3f? = null

    var measurementText: String? = null
        private set
    var measurementPos: Vector2f? = null
        private set

    fun update(camera: CameraComponent) {
        // Reset if not in use
        if (!inUse) {
            startPoint = null
            endPoint = null
            measurementText = null
            measurementPos = null
            return
        }

        val viewportSize = inputProvider.getGameViewportSize()

        val relX = inputProvider.getMouseScreenX()
        val relY = inputProvider.getMouseScreenX()

        measurementText = null
        measurementPos = null

        if (relX >= 0 && relX <= viewportSize.x && relY >= 0 && relY <= viewportSize.y) {
            val ray = camera.screenToRay(relX, relY, viewportSize.x, viewportSize.y)

            if (abs(ray.direction.y) > 0.0001f) {
                val t = -ray.origin.y / ray.direction.y
                if (abs(t) > 0) {
                    val hitPoint = Vector3f(ray.direction).mul(t).add(ray.origin)

                    if (inputProvider.isLeftMouseButtonDown(true)) {
                        if (startPoint == null) {
                            startPoint = Vector3f(hitPoint)
                            endPoint = null
                        } else {
                            endPoint = Vector3f(hitPoint)
                        }
                    }

                    startPoint?.let { start ->
                        val currentEnd = endPoint ?: hitPoint
                        debugRenderer.addLine3D(start, currentEnd, Vector3f(1f, 1f, 0f))

                        val distance = start.distance(currentEnd)
                        val settings = settingsManager.editorSettings
                        val displayText = if (settings.unitSystem == UnitSystem.METRIC) {
                            String.format("%.2f m", distance)
                        } else {
                            val feet = Units.fromMeters(distance.toDouble(), UnitType.FEET)
                            String.format("%.2f ft", feet)
                        }

                        measurementText = "Distance: $displayText"
                        measurementPos = Vector2f(inputProvider.getMouseX() + 20, inputProvider.getMouseY() + 20)
                    }
                }
            }
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            startPoint = null
            endPoint = null
        }
    }
}