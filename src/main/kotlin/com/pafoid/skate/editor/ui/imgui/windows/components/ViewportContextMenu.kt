package com.pafoid.skate.editor.ui.imgui.windows.components

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.windows.LightType
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import imgui.ImGui
import imgui.ImVec2
import org.joml.Vector3f

/**
 * Renders the viewport context menu with creation and manipulation options.
 * 
 * This component handles:
 * - Create Empty GameObject
 * - Create 3D Objects (Cube, Sphere, Cylinder, Plane)
 * - Create Lights (Directional, Point, Spot)
 * - Create Camera
 * - Create Skateboard Obstacles (Rail, Ledge, Kicker, etc.)
 * - Object manipulation (Duplicate, Delete)
 * - Focus and Reset Camera
 * 
 * @param stringManager For localized menu strings
 */
class ViewportContextMenu(
    private val stringManager: StringManager
) {
    
    companion object {
        private const val TOOLBAR_HEIGHT = 40f
    }
    
    /**
     * Renders the context menu.
     * 
     * @param windowPos The window position for calculating menu position
     * @param scene The current scene for object creation
     * @param callbacks Callbacks for menu actions
     */
    fun render(
        windowPos: ImVec2,
        scene: Scene?,
        callbacks: ViewportContextMenuCallbacks
    ) {
        ImGui.setCursorPos(windowPos.x, windowPos.y + TOOLBAR_HEIGHT)
        
        if (ImGui.beginPopupContextWindow("ViewportContextMenu")) {
            ImGui.text(stringManager.getString("context.viewport.title"))
            ImGui.separator()
            
            renderCreateMenu(scene, callbacks)
            renderObjectManipulationMenu(scene, callbacks)
            renderCameraMenu(scene, callbacks)
            
            ImGui.endPopup()
        }
    }
    
    private fun renderCreateMenu(scene: Scene?, callbacks: ViewportContextMenuCallbacks) {
        // Create Empty
        if (ImGui.menuItem("${Icons.PLUS} ${stringManager.getString("context.viewport.create_empty")}")) {
            scene?.let { callbacks.onCreateEmpty(it) }
        }
        
        // Create 3D Object submenu
        if (ImGui.beginMenu("${Icons.CUBE} ${stringManager.getString("context.viewport.create_3d_object")}")) {
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.cube"))) {
                callbacks.onCreatePrimitive("Cube", Vector3f(0.5f, 0.5f, 0.5f))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.sphere"))) {
                callbacks.onCreatePrimitive("Sphere", Vector3f(0.5f, 0.5f, 0.5f))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.cylinder"))) {
                callbacks.onCreatePrimitive("Cylinder", Vector3f(0.5f, 1f, 0.5f))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.plane"))) {
                callbacks.onCreatePrimitive("Plane", Vector3f(5f, 0f, 5f))
            }
            ImGui.endMenu()
        }
        
        // Create Light submenu
        if (ImGui.beginMenu("${Icons.SUN} ${stringManager.getString("context.viewport.create_light")}")) {
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_light.directional"))) {
                callbacks.onCreateLight("DirectionalLight", LightType.DIRECTIONAL)
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_light.point"))) {
                callbacks.onCreateLight("PointLight", LightType.POINT)
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_light.spot"))) {
                callbacks.onCreateLight("SpotLight", LightType.SPOT)
            }
            ImGui.endMenu()
        }
        
        // Create Camera
        if (ImGui.menuItem("${Icons.CAMERA} ${stringManager.getString("context.viewport.create_camera")}")) {
            scene?.let { callbacks.onCreateCamera(it) }
        }
        
        ImGui.separator()
        
        // Create Skateboard Obstacle submenu
        if (ImGui.beginMenu("${Icons.GEAR} ${stringManager.getString("context.viewport.create_obstacle")}")) {
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.rail"))) {
                callbacks.onSpawnRail()
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.ledge"))) {
                callbacks.onSpawnLedge()
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.kicker"))) {
                callbacks.onSpawnKicker()
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.manual_pad"))) {
                callbacks.onSpawnManualPad()
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.bank"))) {
                callbacks.onSpawnBank()
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.quarter_pipe"))) {
                callbacks.onSpawnQuarterPipe()
            }
            ImGui.endMenu()
        }
        
        ImGui.separator()
    }
    
    private fun renderObjectManipulationMenu(scene: Scene?, callbacks: ViewportContextMenuCallbacks) {
        val selectedObject = scene?.getSelectedGameObject()
        if (selectedObject != null) {
            if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.viewport.duplicate")}")) {
                callbacks.onDuplicate(selectedObject)
            }
            if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.viewport.delete")}")) {
                callbacks.onDelete(selectedObject, scene)
            }
            ImGui.separator()
        }
        
        // Focus Selected
        if (ImGui.menuItem("${Icons.EYE} ${stringManager.getString("context.viewport.focus_selected")}")) {
            scene?.let { callbacks.onFocusSelected(it) }
        }
        
        // Reset Camera
        if (ImGui.menuItem("${Icons.ROTATE} ${stringManager.getString("context.viewport.reset_camera")}")) {
            scene?.let { callbacks.onResetCamera(it) }
        }
    }
    
    private fun renderCameraMenu(scene: Scene?, callbacks: ViewportContextMenuCallbacks) {
        // Additional camera options can be added here
    }
}

/**
 * Callbacks for viewport context menu actions.
 */
interface ViewportContextMenuCallbacks {
    fun onCreateEmpty(scene: Scene)
    fun onCreatePrimitive(name: String, halfExtents: Vector3f)
    fun onCreateLight(name: String, type: LightType)
    fun onCreateCamera(scene: Scene)
    fun onSpawnRail()
    fun onSpawnLedge()
    fun onSpawnKicker()
    fun onSpawnManualPad()
    fun onSpawnBank()
    fun onSpawnQuarterPipe()
    fun onDuplicate(gameObject: com.pafoid.skate.engine.ecs.GameObject)
    fun onDelete(gameObject: com.pafoid.skate.engine.ecs.GameObject, scene: Scene)
    fun onFocusSelected(scene: Scene)
    fun onResetCamera(scene: Scene)
}
