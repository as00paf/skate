package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabType
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.ViewportCreateCamera
import com.pafoid.skate.engine.events.ViewportCreateEmpty
import com.pafoid.skate.engine.events.ViewportCreateLight
import com.pafoid.skate.engine.events.ViewportCreatePrimitive
import com.pafoid.skate.engine.events.ViewportDelete
import com.pafoid.skate.engine.events.ViewportDuplicate
import com.pafoid.skate.engine.events.ViewportFocusSelected
import com.pafoid.skate.engine.events.ViewportResetCamera
import com.pafoid.skate.engine.events.ViewportSpawnPrefab
import com.pafoid.skate.engine.render.data.LightType
import imgui.ImGui
import imgui.ImVec2

/**
 * Renders the viewport context menu with creation and manipulation options.
 *
 * This component publishes [ViewportAction] events when menu items are selected.
 * The [EventSystem] delivers these events to [com.pafoid.skate.editor.ui.handlers.ViewportActionHandler]
 * which executes the appropriate commands.
 *
 * @param stringManager For localized menu strings
 * @param eventSystem Event system for publishing viewport actions
 */
class ViewportContextMenu(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
) {

    companion object {
        private const val TAB_BAR_HEIGHT = 25f
        private const val TOOLBAR_HEIGHT = 40f
        private const val CONTENT_AREA_START_Y = TAB_BAR_HEIGHT + TOOLBAR_HEIGHT
    }

    /**
     * Renders the context menu.
     *
     * Only triggers when right-clicking in the viewport content area (below tab bar + toolbar).
     *
     * @param windowPos The window position for calculating menu position
     * @param scene The current scene for object creation
     */
    fun render(windowPos: ImVec2, scene: Scene?) {
        // Only trigger context menu when clicking below the tab bar + toolbar area
        val mousePos = ImGui.getMousePos()
        val relativeMouseY = mousePos.y - windowPos.y

        if (relativeMouseY < CONTENT_AREA_START_Y) return

        ImGui.setCursorPos(windowPos.x, windowPos.y + TOOLBAR_HEIGHT)

        if (ImGui.beginPopupContextWindow("ViewportContextMenu")) {
            ImGui.text(stringManager.getString("context.viewport.title"))
            ImGui.separator()

            renderCreateMenu(scene)
            renderObjectManipulationMenu(scene)
            renderCameraMenu(scene)

            ImGui.endPopup()
        }
    }

    private fun renderCreateMenu(scene: Scene?) {
        // Create Empty
        if (ImGui.menuItem("${Icons.PLUS} ${stringManager.getString("context.viewport.create_empty")}")) {
            scene?.let { eventSystem.publish(ViewportCreateEmpty(it)) }
        }

        // Create 3D Object submenu
        if (ImGui.beginMenu("${Icons.CUBE} ${stringManager.getString("context.viewport.create_3d_object")}")) {
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.cube"))) {
                eventSystem.publish(ViewportCreatePrimitive("Cube", org.joml.Vector3f(0.5f, 0.5f, 0.5f)))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.sphere"))) {
                eventSystem.publish(ViewportCreatePrimitive("Sphere", org.joml.Vector3f(0.5f, 0.5f, 0.5f)))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.cylinder"))) {
                eventSystem.publish(ViewportCreatePrimitive("Cylinder", org.joml.Vector3f(0.5f, 1f, 0.5f)))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.plane"))) {
                eventSystem.publish(ViewportCreatePrimitive("Plane", org.joml.Vector3f(5f, 0f, 5f)))
            }
            ImGui.endMenu()
        }

        // Create Light submenu
        if (ImGui.beginMenu("${Icons.SUN} ${stringManager.getString("context.viewport.create_light")}")) {
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_light.directional"))) {
                eventSystem.publish(ViewportCreateLight("DirectionalLight", LightType.DIRECTIONAL))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_light.point"))) {
                eventSystem.publish(ViewportCreateLight("PointLight", LightType.POINT))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_light.spot"))) {
                eventSystem.publish(ViewportCreateLight("SpotLight", LightType.SPOT))
            }
            ImGui.endMenu()
        }

        // Create Camera
        if (ImGui.menuItem("${Icons.CAMERA} ${stringManager.getString("context.viewport.create_camera")}")) {
            scene?.let { eventSystem.publish(ViewportCreateCamera(it)) }
        }

        ImGui.separator()

        // Create Skateboard Obstacle submenu
        if (ImGui.beginMenu("${Icons.GEAR} ${stringManager.getString("context.viewport.create_obstacle")}")) {
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.rail"))) {
                eventSystem.publish(ViewportSpawnPrefab(PrefabType.RAIL))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.ledge"))) {
                eventSystem.publish(ViewportSpawnPrefab(PrefabType.LEDGE))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.kicker"))) {
                eventSystem.publish(ViewportSpawnPrefab(PrefabType.KICKER))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.manual_pad"))) {
                eventSystem.publish(ViewportSpawnPrefab(PrefabType.MANUAL_PAD))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.bank"))) {
                eventSystem.publish(ViewportSpawnPrefab(PrefabType.BANK))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.quarter_pipe"))) {
                eventSystem.publish(ViewportSpawnPrefab(PrefabType.QUARTER_PIPE))
            }
            ImGui.endMenu()
        }

        ImGui.separator()
    }

    private fun renderObjectManipulationMenu(scene: Scene?) {
        val selectedObject = scene?.selectedGameObject
        if (selectedObject != null) {
            if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.viewport.duplicate")}")) {
                eventSystem.publish(ViewportDuplicate(selectedObject))
            }
            if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.viewport.delete")}")) {
                scene?.let { eventSystem.publish(ViewportDelete(selectedObject, it)) }
            }
            ImGui.separator()
        }

        // Focus Selected
        if (ImGui.menuItem("${Icons.EYE} ${stringManager.getString("context.viewport.focus_selected")}")) {
            eventSystem.publish(ViewportFocusSelected)
        }

        // Reset Camera
        if (ImGui.menuItem("${Icons.ROTATE} ${stringManager.getString("context.viewport.reset_camera")}")) {
            eventSystem.publish(ViewportResetCamera)
        }
    }

    private fun renderCameraMenu(scene: Scene?) {
        // Additional camera options can be added here
    }
}
