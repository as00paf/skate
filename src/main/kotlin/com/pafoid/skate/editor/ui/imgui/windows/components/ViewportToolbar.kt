package com.pafoid.skate.editor.ui.imgui.windows.components

import com.pafoid.skate.editor.gizmos.MeasureTool
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCol
import org.joml.Vector3f

/**
 * Renders the viewport toolbar with gizmo, play, and utility buttons.
 * 
 * This component handles:
 * - Gizmo tool selection (Select, Translate, Rotate, Scale, Measure)
 * - Play/Pause/Stop simulation controls
 * - Scene reset and physics debug toggles
 * - Screenshot capture
 * 
 * @param sceneManager For accessing current scene and systems
 * @param engine For runtime playing state control
 * @param logger For logging toolbar actions
 */
class ViewportToolbar(
    private val sceneManager: SceneManager,
    private val engine: Engine,
    private val logger: LoggerService
) {
    
    companion object {
        private const val TOOLBAR_HEIGHT = 40f
        private const val TOOLBAR_BUTTON_HEIGHT = 30f
        private const val TOOLBAR_BUTTON_SPACING = 10f
    }
    
    /**
     * Renders the toolbar at the specified position.
     * 
     * @param windowPos The window position for calculating toolbar position
     */
    fun render(windowPos: ImVec2) {
        val isPlaying = engine.runtimePlaying
        val scene = sceneManager.currentScene
        val toolbarPosY = windowPos.y + TOOLBAR_BUTTON_SPACING / 2f + ImGui.getStyle().framePaddingY
        
        val buttons = buildButtons(scene, isPlaying)
        renderButtons(toolbarPosY, buttons)
    }
    
    private fun buildButtons(scene: Scene?, isPlaying: Boolean): List<() -> Unit> {
        val buttons = mutableListOf<() -> Unit>()
        val gizmoSystem = scene?.systemManager?.getSystem<GizmoSystem>()
        
        if (gizmoSystem != null && !isPlaying) {
            addGizmoButtons(buttons, gizmoSystem, scene)
        }
        
        addPlaybackButtons(buttons, scene, isPlaying)
        addUtilityButtons(buttons, scene)
        
        return buttons
    }
    
    private fun addGizmoButtons(
        buttons: MutableList<() -> Unit>,
        gizmoSystem: GizmoSystem,
        scene: Scene
    ) {
        // Select Tool
        buttons.add {
            val isActive = gizmoSystem.usingGizmo == GizmoSystem.SELECTION_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.MOUSE_POINTER, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                gizmoSystem.toggleGizmo(GizmoSystem.SELECTION_GIZMO)
            }
            if (isActive) ImGui.popStyleColor()
            if (ImGui.isItemHovered()) ImGui.setTooltip("Select Tool (Q)")
        }
        
        // Translate Tool
        buttons.add {
            val isActive = gizmoSystem.usingGizmo == GizmoSystem.TRANSLATE_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.MOVE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                gizmoSystem.toggleGizmo(GizmoSystem.TRANSLATE_GIZMO)
            }
            if (isActive) ImGui.popStyleColor()
            if (ImGui.isItemHovered()) ImGui.setTooltip("Translate Tool (W)")
        }
        
        // Rotate Tool
        buttons.add {
            val isActive = gizmoSystem.usingGizmo == GizmoSystem.ROTATION_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.ROTATE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                gizmoSystem.toggleGizmo(GizmoSystem.ROTATION_GIZMO)
            }
            if (isActive) ImGui.popStyleColor()
            if (ImGui.isItemHovered()) ImGui.setTooltip("Rotate Tool (E)")
        }
        
        // Scale Tool
        buttons.add {
            val isActive = gizmoSystem.usingGizmo == GizmoSystem.SCALE_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.SCALE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                gizmoSystem.toggleGizmo(GizmoSystem.SCALE_GIZMO)
            }
            if (isActive) ImGui.popStyleColor()
            if (ImGui.isItemHovered()) ImGui.setTooltip("Scale Tool (R)")
        }
        
        // Measure Tool
        buttons.add {
            val isActive = gizmoSystem.usingGizmo == GizmoSystem.MEASURE_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.RULER, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                gizmoSystem.toggleGizmo(GizmoSystem.MEASURE_GIZMO)
            }
            if (isActive) {
                ImGui.popStyleColor()
                scene.systemManager.getSystem<MeasureTool>()?.let { tool ->
                    tool.measurementText?.let { text ->
                        tool.measurementPos?.let { pos ->
                            ImGui.setNextWindowPos(pos.x, pos.y)
                            ImGui.beginTooltip()
                            ImGui.text(text)
                            ImGui.endTooltip()
                        }
                    }
                }
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Measure Tool (M)")
        }
    }
    
    private fun addPlaybackButtons(
        buttons: MutableList<() -> Unit>,
        scene: Scene?,
        isPlaying: Boolean
    ) {
        if (isPlaying) {
            // Pause/Resume button
            buttons.add {
                val timeScale = scene?.getTimeScale() ?: 1.0f
                if (timeScale == 1.0f) {
                    if (ImGui.button(Icons.PAUSE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                        scene?.setTimeScale(0.0f)
                        logger.logEditor("Simulation paused")
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip("Pause Simulation (Time Scale: 0.0)")
                } else {
                    if (ImGui.button(Icons.PLAY, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                        scene?.setTimeScale(1.0f)
                        logger.logEditor("Simulation resumed")
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip("Resume Simulation (Time Scale: 1.0)")
                }
            }
            // Stop button
            buttons.add {
                if (ImGui.button(Icons.STOP, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    engine.runtimePlaying = false
                    scene?.setTimeScale(1.0f)
                    logger.logEditor("Simulation stopped")
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("Stop Simulation")
            }
        } else {
            // Play button
            buttons.add {
                if (ImGui.button(Icons.PLAY, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    engine.runtimePlaying = true
                    logger.logEditor("Simulation started")
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("Play Simulation")
            }
        }
    }
    
    private fun addUtilityButtons(
        buttons: MutableList<() -> Unit>,
        scene: Scene?
    ) {
        // Reset Scene button
        buttons.add {
            if (ImGui.button(Icons.GEAR, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                scene?.let {
                    it.gameObjectManager.gameObjects.find { obj -> obj.name == "Skateboard" }?.let { skate ->
                        skate.getComponent<Transform>()?.translation?.set(0f, 0.5f, 0f)
                        skate.getComponent<Transform>()?.rotation?.set(0f, 0f, 0f)
                        val rb = skate.getComponent<RigidBody3D>()
                        rb?.linearVelocity = Vector3f(0f, 0f, 0f)
                        rb?.angularVelocity = Vector3f(0f, 0f, 0f)
                        logger.logEditor("Scene reset")
                    }
                    it.camera.position.set(0f, 5f, 20f)
                    it.camera.yaw = 0f
                }
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Reset Scene")
        }
        
        // Physics Debug button
        buttons.add {
            val physicsDebugEnabled = scene?.physics3d?.debugEnabled ?: false
            if (physicsDebugEnabled) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            }
            if (ImGui.button(Icons.ATOM, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                scene?.physics3d?.debugEnabled = !physicsDebugEnabled
                logger.logEditor("Physics debug toggled")
            }
            if (physicsDebugEnabled) {
                ImGui.popStyleColor()
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Toggle Physics Debug Wireframe")
        }
        
        // Screenshot button
        buttons.add {
            // Screenshot functionality would be called from GameViewWindow
            // This is just the button rendering
            if (ImGui.button(Icons.CAMERA, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                // Signal to take screenshot - handled by GameViewWindow
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Take Screenshot")
        }
    }
    
    private fun renderButtons(toolbarPosY: Float, buttons: List<() -> Unit>) {
        val totalButtonWidth = (TOOLBAR_BUTTON_HEIGHT * buttons.size) + (TOOLBAR_BUTTON_SPACING * (buttons.size - 1))
        val toolbarPosX = TOOLBAR_BUTTON_SPACING / 2f + ImGui.getStyle().framePaddingX
        
        ImGui.setCursorPos(toolbarPosX, toolbarPosY)
        ImGui.beginChild(
            "GameViewportToolbar",
            totalButtonWidth,
            TOOLBAR_HEIGHT,
            false,
            imgui.flag.ImGuiWindowFlags.NoBackground or imgui.flag.ImGuiWindowFlags.NoDecoration
        )
        
        buttons.forEachIndexed { index, button ->
            button()
            if (index < buttons.size - 1) {
                ImGui.sameLine(0f, TOOLBAR_BUTTON_SPACING)
            }
        }
        
        ImGui.endChild()
    }
    
    /**
     * Get the toolbar height constant.
     */
    fun getToolbarHeight(): Float = TOOLBAR_HEIGHT
}
