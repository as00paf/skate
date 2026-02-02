package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.Prefabs
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.utils.SettingsManager
import com.pafoid.skate.engine.utils.UnitSystem
import com.pafoid.skate.engine.utils.Units
import com.pafoid.skate.engine.utils.UnitType
import imgui.ImGui
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue
import kotlin.math.abs

class MeasureTool : Component(), KoinComponent {
    private val debugDraw: DebugDraw by inject()
    private val sceneManager: SceneManager by inject()
    private val mouseListener: MouseListener by inject()
    private val settingsManager: SettingsManager by inject()

    private var startPoint: Vector3f? = null
    private var endPoint: Vector3f? = null
    private var isActive = false

    var measurementText: String? = null
        private set
    var measurementPos: org.joml.Vector2f? = null
        private set

    override fun editorUpdate(dt: Float) {
        if (!isActive) return

        val scene = sceneManager.currentScene ?: return
        val mousePos = ImGui.getMousePos() // This might still be unsafe if called outside ImGui frame? 
                                          // Actually getMousePos is usually safe as it reads IO.
                                          // But let's use MouseListener inputs if possible or assume IO is updated.
        val viewportSize = mouseListener.getGameViewportSize()
        val viewportPos = mouseListener.getGameViewportPos()

        val relX = mousePos.x - viewportPos.x
        val relY = mousePos.y - viewportPos.y
        
        // Clear previous text
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
                        debugDraw.addLine3D(start, currentEnd, Vector3f(1f, 1f, 0f))
                        
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
                        measurementPos = org.joml.Vector2f(mousePos.x + 20, mousePos.y + 20)
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
        setToolActive(!isActive)
    }
    
    fun setToolActive(active: Boolean) {
        isActive = active
        if (!isActive) {
            startPoint = null
            endPoint = null
        }
    }

    fun isToolActive() = isActive
}
