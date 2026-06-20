package com.pafoid.skate.editor.ui.windows.viewport

import com.pafoid.skate.editor.events.ViewportAction.ScreenshotRequested
import com.pafoid.skate.editor.events.ViewportAction.ToggleGizmo
import com.pafoid.skate.editor.events.ViewportAction.TogglePhysicsDebug
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants.SEPARATOR_SPACING
import com.pafoid.skate.editor.imgui.data.UiConstants.SEPARATOR_WIDTH
import com.pafoid.skate.editor.imgui.data.UiConstants.TOOLBAR_BUTTON_HEIGHT
import com.pafoid.skate.editor.imgui.data.UiConstants.TOOLBAR_BUTTON_SPACING
import com.pafoid.skate.editor.imgui.data.UiConstants.TOOLBAR_HEIGHT
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.logEditor
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags

class ViewportToolbar(
    private val sceneManager: SceneManager,
    private val engine: Engine,
    private val logger: LoggerService,
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
    private val gizmoSystem: GizmoSystem,
) {
    fun render(windowPos: ImVec2) {
        val isPlaying = engine.runtimePlaying
        val scene = sceneManager.currentScene
        val toolbarPosY = windowPos.y + TOOLBAR_BUTTON_SPACING / 2f + ImGui.getStyle().framePaddingY

        val groups = buildButtonGroups(scene, isPlaying)
        renderButtonGroups(toolbarPosY, groups)
    }

    private fun buildButtonGroups(scene: Scene?, isPlaying: Boolean): List<List<() -> Unit>> {
        val groups = mutableListOf<List<() -> Unit>>()

        if (!isPlaying) {
            val gizmoButtons = mutableListOf<() -> Unit>()
            addGizmoButtons(gizmoButtons)
            groups.add(gizmoButtons)
        }

        val playbackButtons = mutableListOf<() -> Unit>()
        addPlaybackButtons(playbackButtons, scene, isPlaying)
        groups.add(playbackButtons)

        val utilityButtons = mutableListOf<() -> Unit>()
        addUtilityButtons(utilityButtons, scene)
        groups.add(utilityButtons)

        return groups
    }

    private fun addGizmoButtons(buttons: MutableList<() -> Unit>) {
        // Select Tool
        buttons.add {
            val isActive = gizmoSystem.usingGizmo == GizmoSystem.SELECTION_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.MOUSE_POINTER, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                eventSystem.publish(ToggleGizmo(GizmoSystem.SELECTION_GIZMO))
            }
            if (isActive) ImGui.popStyleColor()
            if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.select_tool"))
        }

        // Translate Tool
        buttons.add {
            val isActive = gizmoSystem.usingGizmo == GizmoSystem.TRANSLATE_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.MOVE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                eventSystem.publish(ToggleGizmo(GizmoSystem.TRANSLATE_GIZMO))
            }
            if (isActive) ImGui.popStyleColor()
            if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.translate_tool"))
        }

        // Rotate Tool
        buttons.add {
            val isActive = gizmoSystem.usingGizmo == GizmoSystem.ROTATION_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.ROTATE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                eventSystem.publish(ToggleGizmo(GizmoSystem.ROTATION_GIZMO))
            }
            if (isActive) ImGui.popStyleColor()
            if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.rotate_tool"))
        }

        // Scale Tool
        buttons.add {
            val isActive = gizmoSystem.usingGizmo == GizmoSystem.SCALE_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.SCALE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                eventSystem.publish(ToggleGizmo(GizmoSystem.SCALE_GIZMO))
            }
            if (isActive) ImGui.popStyleColor()
            if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.scale_tool"))
        }

        // Measure Tool
        buttons.add {
            val isActive = gizmoSystem.usingGizmo == GizmoSystem.MEASURE_GIZMO
            if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            if (ImGui.button(Icons.RULER, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                eventSystem.publish(ToggleGizmo(GizmoSystem.MEASURE_GIZMO))
            }
            if (isActive) {
                ImGui.popStyleColor()
                gizmoSystem.measureGizmo.let { tool ->
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
            // Pause/Resume button — visual state reflects time scale
            buttons.add {
                val timeScale = scene?.getComponent<TimeComponent>()?.timeScale ?: 1.0f
                if (timeScale == 1.0f) {
                    // Currently running — show Pause
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.4f, 0.1f, 1f)
                    if (ImGui.button(Icons.PAUSE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                        eventSystem.publish(EngineAction.SetRuntimePlaying(false))
                        logger.logEditor("Simulation paused")
                    }
                    ImGui.popStyleColor()
                    if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.pause_simulation"))
                } else {
                    // Paused — show Resume
                    if (ImGui.button(Icons.PLAY, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                        eventSystem.publish(EngineAction.SetRuntimePlaying(true))
                        logger.logEditor("Simulation resumed")
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.resume_simulation"))
                }
            }
            // Stop button — highlighted to indicate active play state
            buttons.add {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.1f, 0.1f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.8f, 0.2f, 0.2f, 1f)
                if (ImGui.button(Icons.STOP, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    eventSystem.publish(EngineAction.SetRuntimePlaying(false))
                    logger.logEditor("Simulation stopped")
                }
                ImGui.popStyleColor(2)
                if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.viewport_toolbar.stop_simulation"))
            }
        } else {
            // Play button
            buttons.add {
                if (ImGui.button(Icons.PLAY, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    eventSystem.publish(EngineAction.SetRuntimePlaying(true))
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
            val physicsDebugEnabled = scene?.getComponent<ScenePhysicsComponent>()?.debugEnabled ?: false
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

    private fun renderButtonGroups(toolbarPosY: Float, groups: List<List<() -> Unit>>) {
        val buttonCount = groups.sumOf { it.size }
        val separatorCount = (groups.size - 1).coerceAtLeast(0)
        // Only count intra-group spacings — inter-group gaps are part of separator width
        val intraGroupSpacings = groups.sumOf { (it.size - 1).coerceAtLeast(0) }
        val totalButtonWidth = (TOOLBAR_BUTTON_HEIGHT * buttonCount) +
                (TOOLBAR_BUTTON_SPACING * intraGroupSpacings) +
                (SEPARATOR_SPACING * 2 + SEPARATOR_WIDTH) * separatorCount

        val toolbarPosX = TOOLBAR_BUTTON_SPACING / 2f + ImGui.getStyle().framePaddingX

        ImGui.setCursorPos(toolbarPosX, toolbarPosY)
        ImGui.beginChild(
            "GameViewportToolbar",
            totalButtonWidth,
            TOOLBAR_HEIGHT,
            false,
            ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoDecoration
        )

        groups.forEachIndexed { groupIndex, buttons ->
            if (groupIndex > 0) {
                ImGui.sameLine(0f, SEPARATOR_SPACING)
            }

            buttons.forEachIndexed { buttonIndex, button ->
                button()
                if (buttonIndex < buttons.size - 1) {
                    ImGui.sameLine(0f, TOOLBAR_BUTTON_SPACING)
                }
            }
        }

        ImGui.endChild()
    }
}
