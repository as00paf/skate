package com.pafoid.skate.editor.ui.windows.viewport

import com.pafoid.skate.editor.events.SceneAction
import com.pafoid.skate.editor.events.ViewportAction.ScreenshotRequested
import com.pafoid.skate.editor.events.ViewportAction.SetRuntimePlaying
import com.pafoid.skate.editor.events.ViewportAction.SetSimulationTimeScale
import com.pafoid.skate.editor.events.ViewportAction.ToggleGizmo
import com.pafoid.skate.editor.events.ViewportAction.TogglePhysicsDebug
import com.pafoid.skate.editor.gizmos.MeasureTool
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags

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
 * @param stringManager For localized tooltips
 */
class ViewportToolbar(
    private val sceneManager: SceneManager,
    private val engine: Engine,
    private val logger: LoggerService,
    private val stringManager: StringManager,
    private val systemManager: SystemManager,
    private val eventSystem: EventSystem,
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
        
        if (!isPlaying) {
            addGizmoButtons(buttons)
        }
        
        addPlaybackButtons(buttons, scene, isPlaying)
        addUtilityButtons(buttons, scene)
        
        return buttons
    }
    
    private fun addGizmoButtons(
        buttons: MutableList<() -> Unit>
    ) {
        val gizmoSystem = systemManager.getSystem<GizmoSystem>()
        // Select Tool
        buttons.add {
            val isActive = gizmoSystem?.usingGizmo == GizmoSystem.SELECTION_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.MOUSE_POINTER, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                eventSystem.publish(ToggleGizmo(GizmoSystem.SELECTION_GIZMO))
            }
            if (isActive) ImGui.popStyleColor()
            if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.select_tool"))
        }

        // Translate Tool
        buttons.add {
            val isActive = gizmoSystem?.usingGizmo == GizmoSystem.TRANSLATE_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.MOVE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                eventSystem.publish(ToggleGizmo(GizmoSystem.TRANSLATE_GIZMO))
            }
            if (isActive) ImGui.popStyleColor()
            if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.translate_tool"))
        }

        // Rotate Tool
        buttons.add {
            val isActive = gizmoSystem?.usingGizmo == GizmoSystem.ROTATION_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.ROTATE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                eventSystem.publish(ToggleGizmo(GizmoSystem.ROTATION_GIZMO))
            }
            if (isActive) ImGui.popStyleColor()
            if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.rotate_tool"))
        }

        // Scale Tool
        buttons.add {
            val isActive = gizmoSystem?.usingGizmo == GizmoSystem.SCALE_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.SCALE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                eventSystem.publish(ToggleGizmo(GizmoSystem.SCALE_GIZMO))
            }
            if (isActive) ImGui.popStyleColor()
            if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.scale_tool"))
        }

        // Measure Tool
        buttons.add {
            val isActive = gizmoSystem?.usingGizmo == GizmoSystem.MEASURE_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.RULER, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                eventSystem.publish(ToggleGizmo(GizmoSystem.MEASURE_GIZMO))
            }
            if (isActive) {
                ImGui.popStyleColor()
                systemManager.getSystem<MeasureTool>()?.let { tool ->
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
            if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.measure_tool"))
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
                val timeScale = scene?.getComponent<TimeComponent>()?.timeScale ?: 1.0f
                if (timeScale == 1.0f) {
                    if (ImGui.button(Icons.PAUSE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                        eventSystem.publish(SetSimulationTimeScale(0.0f))
                        logger.logEditor("Simulation paused")
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.pause_simulation"))
                } else {
                    if (ImGui.button(Icons.PLAY, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                        eventSystem.publish(SetSimulationTimeScale(1.0f))
                        logger.logEditor("Simulation resumed")
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.resume_simulation"))
                }
            }
            // Stop button
            buttons.add {
                if (ImGui.button(Icons.STOP, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    eventSystem.publish(SetRuntimePlaying(false))
                    logger.logEditor("Simulation stopped")
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.stop_simulation"))
            }
        } else {
            // Play button
            buttons.add {
                if (ImGui.button(Icons.PLAY, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    eventSystem.publish(SetRuntimePlaying(true))
                    logger.logEditor("Simulation started")
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.play_simulation"))
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
                eventSystem.publish(SceneAction.ResetScene)
                logger.logEditor("Scene reset")
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.reset_scene"))
        }

        // Physics Debug button
        buttons.add {
            val physicsDebugEnabled = scene?.physics3d?.debugEnabled ?: false
            if (physicsDebugEnabled) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            }
            if (ImGui.button(Icons.ATOM, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                eventSystem.publish(TogglePhysicsDebug)
                logger.logEditor("Physics debug toggled")
            }
            if (physicsDebugEnabled) {
                ImGui.popStyleColor()
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.physics_debug"))
        }

        // Screenshot button
        buttons.add {
            if (ImGui.button(Icons.CAMERA, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                eventSystem.publish(ScreenshotRequested)
                logger.logEditor("Screenshot requested")
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.screenshot"))
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
            ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoDecoration
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
