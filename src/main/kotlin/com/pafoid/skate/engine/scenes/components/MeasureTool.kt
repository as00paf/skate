package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.utils.SettingsManager
import com.pafoid.skate.engine.utils.UnitSystem
import com.pafoid.skate.engine.utils.Units
import com.pafoid.skate.engine.utils.UnitType
import imgui.ImGui
import org.joml.Vector3f
import kotlin.math.abs

class MeasureTool : Component() {
    private var startPoint: Vector3f? = null
    private var endPoint: Vector3f? = null
    private var isActive = false

    override fun editorUpdate(dt: Float) {
        if (!isActive) return

        val scene = SceneManager.getCurrentScene() ?: return
        val mousePos = MouseListener.get()
        val viewportSize = MouseListener.getGameViewportSize()
        val viewportPos = MouseListener.getGameViewportPos()

        val relX = mousePos.x - viewportPos.x
        val relY = mousePos.y - viewportPos.y

        if (relX >= 0 && relX <= viewportSize.x && relY >= 0 && relY <= viewportSize.y) {
            val ray = scene.camera.screenToRay(relX, relY, viewportSize.x, viewportSize.y)
            
            // For simplicity, we'll measure on the ground plane (Y=0) 
            // OR we could raycast against physics objects.
            // Let's try to raycast against the ground plane first.
            if (abs(ray.direction.y) > 0.0001f) {
                val t = -ray.origin.y / ray.direction.y
                if (t > 0) {
                    val hitPoint = Vector3f(ray.direction).mul(t).add(ray.origin)
                    
                    if (MouseListener.mouseButtonBeginPress(0)) {
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
                        DebugDraw.addLine3D(start, currentEnd, Vector3f(1f, 1f, 0f))
                        
                        val distance = start.distance(currentEnd)
                        val settings = SettingsManager.settings
                        val displayText = if (settings.unitSystem == UnitSystem.METRIC) {
                            String.format("%.2f m", distance)
                        } else {
                            val feet = Units.fromMeters(distance.toDouble(), UnitType.FEET)
                            String.format("%.2f ft", feet)
                        }
                        
                        // Draw label at midpoint
                        val mid = Vector3f(start).add(currentEnd).mul(0.5f)
                        // We can't draw text in 3D easily, so we'll show it in a tooltip or overlay
                        ImGui.setNextWindowPos(mousePos.x + 20, mousePos.y + 20)
                        ImGui.beginTooltip()
                        ImGui.text("Distance: $displayText")
                        ImGui.endTooltip()
                    }
                }
            }
        }

        if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) {
            isActive = false
            startPoint = null
            endPoint = null
        }
    }

    fun toggle() {
        isActive = !isActive
        if (!isActive) {
            startPoint = null
            endPoint = null
        }
    }
    
    fun setEnabled(enabled: Boolean) {
        isActive = enabled
        if (!isActive) {
            startPoint = null
            endPoint = null
        }
    }

    fun isEnabled() = isActive
}
