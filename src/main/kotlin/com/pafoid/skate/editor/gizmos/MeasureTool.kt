package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.UnitSystem
import com.pafoid.skate.engine.utils.UnitType
import com.pafoid.skate.engine.utils.Units
import imgui.ImGui
import imgui.flag.ImGuiKey
import org.joml.Vector2f
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import kotlin.math.abs

class MeasureTool(
    mouseListener: MouseListener,
    undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
    private val settingsManager: SettingsManager,
) : Gizmo(mouseListener, undoRedoManager), KoinComponent {
    private var startPoint: Vector3f? = null
    private var endPoint: Vector3f? = null

    var measurementText: String? = null
        private set
    var measurementPos: Vector2f? = null
        private set

    override fun editorUpdate(dt: Float) {
        // Reset if not in use
        if (!isInUse()) {
            startPoint = null
            endPoint = null
            measurementText = null
            measurementPos = null
            return
        }

        val viewportSize = mouseListener.getGameViewportSize()
        val viewportPos = mouseListener.getGameViewportPos()

        // Use MouseListener for consistent state
        val mouseX = mouseListener.getX()
        val mouseY = mouseListener.getY()

        val relX = mouseX - viewportPos.x
        val relY = mouseY - viewportPos.y

        // Clear previous text (will be set if we have a measurement)
        measurementText = null
        measurementPos = null

        if (relX >= 0 && relX <= viewportSize.x && relY >= 0 && relY <= viewportSize.y) {
            val ray = scene.camera.screenToRay(relX, relY, viewportSize.x, viewportSize.y)

            // For simplicity, we'll measure on the ground plane (Y=0)
            // OR we could raycast against physics objects.
            // Let's try to raycast against the ground plane first.
            if (abs(ray.direction.y) > 0.0001f) {
                val t = -ray.origin.y / ray.direction.y
                if (t > 0) {
                    val hitPoint = Vector3f(ray.direction).mul(t).add(ray.origin)

                    if (mouseListener.mouseButtonBeginPress(0)) {
                        if (startPoint == null || (startPoint != null && endPoint != null)) {
                            startPoint = Vector3f(hitPoint)
                            endPoint = null
                        } else {
                            endPoint = Vector3f(hitPoint)
                        }
                    }

                    // Preview line if we have a start point
                    startPoint?.let { start ->
                        val currentEnd = endPoint ?: hitPoint
                        debugRenderer.addLine3D(start, currentEnd, Vector3f(1f, 1f, 0f))

                        val distance = start.distance(currentEnd)
                        val settings = settingsManager.settings
                        val displayText = if (settings.unitSystem == UnitSystem.METRIC) {
                            String.format("%.2f m", distance)
                        } else {
                            val feet = Units.fromMeters(distance.toDouble(), UnitType.FEET)
                            String.format("%.2f ft", feet)
                        }

                        // Store for rendering
                        measurementText = "Distance: $displayText"
                        measurementPos = Vector2f(mouseX + 20, mouseY + 20)
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