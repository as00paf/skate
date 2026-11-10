package com.pafoid.skate.editor.ui.windows.viewport

import com.pafoid.skate.editor.data.EditorShortcut
import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.events.PlayModeAction
import com.pafoid.skate.editor.events.ViewportAction.ScreenshotRequested
import com.pafoid.skate.editor.events.ViewportAction.ToggleGizmo
import com.pafoid.skate.editor.events.ViewportAction.TogglePhysicsDebug
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants.SEPARATOR_SPACING
import com.pafoid.skate.editor.imgui.data.UiConstants.SEPARATOR_WIDTH
import com.pafoid.skate.editor.imgui.data.UiConstants.TOOLBAR_BUTTON_HEIGHT
import com.pafoid.skate.editor.imgui.data.UiConstants.TOOLBAR_BUTTON_SPACING
import com.pafoid.skate.editor.imgui.data.UiConstants.TOOLBAR_HEIGHT
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.editor.systems.PlayMode
import com.pafoid.skate.editor.systems.PlayModeController
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags

/** The viewport's toolbar: gizmos, Play/Pause/Stop, Reset and utilities, laid out by [ViewportToolbarLayout]. */
class ViewportToolbar(
    private val engine: Engine,
    private val stringManager: StringManager,
    private val gizmoSystem: GizmoSystem,
    private val playMode: PlayModeController,
    /** Draws the window's `?` button as a square of the given size (`WindowHeader.helpButton`), supplied by the Game Viewport. */
    private val helpButton: (Float) -> Unit = {},
) {
    private val eventSystem = engine.eventSystem
    private val logger = engine.logger

    fun render(windowPos: ImVec2) {
        val mode = playMode.mode
        val canReset = playMode.canReset
        val scene = engine.sceneManager.currentScene
        val toolbarPosY = windowPos.y + TOOLBAR_BUTTON_SPACING / 2f + ImGui.getStyle().framePaddingY

        val groups = ViewportToolbarLayout.GROUPS.map { group ->
            group.map { button -> { renderSlot(button, ViewportToolbarLayout.isEnabled(button, mode, canReset), mode, scene) } }
        }
        renderButtonGroups(toolbarPosY, groups)
    }

    private fun renderSlot(button: ToolbarButton, enabled: Boolean, mode: PlayMode, scene: Scene?) {
        ImGui.beginDisabled(!enabled)
        renderButton(button, mode, scene)
        ImGui.endDisabled()
    }

    private fun renderButton(button: ToolbarButton, mode: PlayMode, scene: Scene?) {
        when (button) {
            ToolbarButton.SELECT -> gizmoButton(GizmoSystem.SELECTION_GIZMO, Icons.MOUSE_POINTER, "tooltip.viewport_toolbar.select_tool", EditorShortcuts.GIZMO_SELECT)
            ToolbarButton.TRANSLATE -> gizmoButton(GizmoSystem.TRANSLATE_GIZMO, Icons.MOVE, "tooltip.viewport_toolbar.translate_tool", EditorShortcuts.GIZMO_TRANSLATE)
            ToolbarButton.ROTATE -> gizmoButton(GizmoSystem.ROTATION_GIZMO, Icons.ROTATE, "tooltip.viewport_toolbar.rotate_tool", EditorShortcuts.GIZMO_ROTATE)
            ToolbarButton.SCALE -> gizmoButton(GizmoSystem.SCALE_GIZMO, Icons.SCALE, "tooltip.viewport_toolbar.scale_tool", EditorShortcuts.GIZMO_SCALE)
            ToolbarButton.GIZMO_SPACE -> {
                val key = if (gizmoSystem.localSpace) "lbl.viewport_toolbar.space_local" else "lbl.viewport_toolbar.space_world"
                if (stringManager.tippedWith(ImGui.button(stringManager.getString(key) + "##gizmoSpace"), "tooltip.viewport_toolbar.gizmo_space")) {
                    gizmoSystem.toggleSpace()
                }
            }
            ToolbarButton.SNAP -> {
                val on = gizmoSystem.snapEnabled
                if (on) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
                if (stringManager.tippedWith(ImGui.button(stringManager.getString("lbl.viewport_toolbar.snap") + "##gizmoSnap"), "tooltip.viewport_toolbar.snap")) {
                    gizmoSystem.toggleSnap()
                }
                if (on) ImGui.popStyleColor()
            }
            ToolbarButton.MEASURE -> {
                gizmoButton(GizmoSystem.MEASURE_GIZMO, Icons.RULER, "tooltip.viewport_toolbar.measure_tool", EditorShortcuts.MEASURE)
                if (gizmoSystem.usingGizmo == GizmoSystem.MEASURE_GIZMO && mode == PlayMode.EDIT) renderMeasurement()
            }
            ToolbarButton.PLAY_PAUSE -> renderPlayPause(mode)
            ToolbarButton.STOP -> {
                val active = mode != PlayMode.EDIT
                if (active) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.1f, 0.1f, 1f)
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.8f, 0.2f, 0.2f, 1f)
                }
                if (stringManager.tippedWith(ImGui.button(Icons.STOP, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT), "tooltip.viewport_toolbar.stop_simulation", EditorShortcuts.PLAY_TOGGLE)) {
                    eventSystem.publish(PlayModeAction.Stop)
                    logger.logEditor("Simulation stopped")
                }
                if (active) ImGui.popStyleColor(2)
            }
            ToolbarButton.RESET -> {
                if (stringManager.tippedWith(ImGui.button(Icons.UNDO, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT), "tooltip.viewport_toolbar.reset_scene")) {
                    eventSystem.publish(PlayModeAction.Reset)
                    logger.logEditor("Scene reset")
                }
            }
            ToolbarButton.PHYSICS_DEBUG -> {
                val on = scene?.getComponent<ScenePhysicsComponent>()?.debugEnabled ?: false
                if (on) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
                if (stringManager.tippedWith(ImGui.button(Icons.ATOM, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT), "tooltip.viewport_toolbar.physics_debug")) {
                    eventSystem.publish(TogglePhysicsDebug)
                    logger.logEditor("Physics debug toggled")
                }
                if (on) ImGui.popStyleColor()
            }
            // A square like the other toolbar buttons, so it fills its slot of the toolbar width (EDT-67).
            ToolbarButton.HELP -> helpButton(TOOLBAR_BUTTON_HEIGHT)
            ToolbarButton.SCREENSHOT -> {
                if (stringManager.tippedWith(ImGui.button(Icons.CAMERA, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT), "tooltip.viewport_toolbar.screenshot")) {
                    eventSystem.publish(ScreenshotRequested)
                    logger.logEditor("Screenshot requested")
                }
            }
        }
    }

    private fun gizmoButton(gizmoId: Int, icon: String, tooltipKey: String, shortcut: EditorShortcut) {
        val isActive = gizmoSystem.usingGizmo == gizmoId
        if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
        if (stringManager.tippedWith(ImGui.button(icon, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT), tooltipKey, shortcut)) {
            eventSystem.publish(ToggleGizmo(gizmoId))
        }
        if (isActive) ImGui.popStyleColor()
    }

    private fun renderMeasurement() {
        val tool = gizmoSystem.measureGizmo
        val text = tool.measurementText ?: return
        val pos = tool.measurementPos ?: return
        ImGui.setNextWindowPos(pos.x, pos.y)
        ImGui.beginTooltip()
        ImGui.text(text)
        ImGui.endTooltip()
    }

    private fun renderPlayPause(mode: PlayMode) {
        if (ViewportToolbarLayout.showsPause(mode)) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.4f, 0.1f, 1f)
            if (stringManager.tippedWith(ImGui.button(Icons.PAUSE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT), "tooltip.viewport_toolbar.pause_simulation")) {
                eventSystem.publish(PlayModeAction.Pause)
                logger.logEditor("Simulation paused")
            }
            ImGui.popStyleColor()
            return
        }
        val tooltipKey = if (mode == PlayMode.PAUSED) "tooltip.viewport_toolbar.resume_simulation" else "tooltip.viewport_toolbar.play_simulation"
        if (stringManager.tippedWith(ImGui.button(Icons.PLAY, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT), tooltipKey, EditorShortcuts.PLAY_TOGGLE)) {
            eventSystem.publish(PlayModeAction.Play)
            logger.logEditor("Simulation started")
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
