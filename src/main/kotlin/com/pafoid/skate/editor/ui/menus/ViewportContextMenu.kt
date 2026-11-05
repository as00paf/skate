package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.game.prefabs.PrefabCatalog
import com.pafoid.skate.game.prefabs.PrefabCategory
import com.pafoid.skate.editor.data.PrimitiveType
import com.pafoid.skate.editor.events.ViewportAction.CreateCamera
import com.pafoid.skate.editor.events.ViewportAction.CreateEmpty
import com.pafoid.skate.editor.events.ViewportAction.CreateLight
import com.pafoid.skate.editor.events.ViewportAction.CreatePrimitive
import com.pafoid.skate.editor.events.ViewportAction.Delete
import com.pafoid.skate.editor.events.ViewportAction.Duplicate
import com.pafoid.skate.editor.events.ViewportAction.FocusSelected
import com.pafoid.skate.editor.events.ViewportAction.ResetCamera
import com.pafoid.skate.editor.events.ViewportAction.SpawnPrefab
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants.CONTENT_AREA_START_Y
import com.pafoid.skate.editor.imgui.data.UiConstants.TOOLBAR_HEIGHT
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.data.LightType
import imgui.ImGui
import imgui.ImVec2
import org.joml.Vector3f

class ViewportContextMenu(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
) {
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

            ImGui.endPopup()
        }
    }

    private fun renderCreateMenu(scene: Scene?) {
        // Create Empty
        if (ImGui.menuItem("${Icons.PLUS} ${stringManager.getString("context.viewport.create_empty")}")) {
            scene?.let { eventSystem.publish(CreateEmpty(it)) }
        }

        // Create 3D Object submenu
        if (ImGui.beginMenu(stringManager.label("context.viewport.create_3d_object", "${Icons.CUBE} "))) {
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.cube"))) {
                eventSystem.publish(CreatePrimitive("Cube", PrimitiveType.CUBE, Vector3f(0.5f, 0.5f, 0.5f)))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.sphere"))) {
                eventSystem.publish(CreatePrimitive("Sphere", PrimitiveType.SPHERE, Vector3f(0.5f, 0.5f, 0.5f)))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.cylinder"))) {
                eventSystem.publish(CreatePrimitive("Cylinder", PrimitiveType.CYLINDER, Vector3f(0.5f, 1f, 0.5f)))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.plane"))) {
                eventSystem.publish(CreatePrimitive("Plane", PrimitiveType.PLANE, Vector3f(5f, 0f, 5f)))
            }
            ImGui.endMenu()
        }

        // Create Light submenu
        if (ImGui.beginMenu(stringManager.label("context.viewport.create_light", "${Icons.SUN} "))) {
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_light.directional"))) {
                eventSystem.publish(CreateLight("DirectionalLight", LightType.DIRECTIONAL))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_light.point"))) {
                eventSystem.publish(CreateLight("PointLight", LightType.POINT))
            }
            if (ImGui.menuItem(stringManager.getString("context.viewport.create_light.spot"))) {
                eventSystem.publish(CreateLight("SpotLight", LightType.SPOT))
            }
            ImGui.endMenu()
        }

        UiCreateMenu.render(stringManager, eventSystem, null)

        // Create Camera
        if (ImGui.menuItem("${Icons.CAMERA} ${stringManager.getString("context.viewport.create_camera")}")) {
            scene?.let { eventSystem.publish(CreateCamera(it)) }
        }

        ImGui.separator()

        // One submenu per prefab picker section (PrefabCatalog), player prefabs excluded.
        if (ImGui.beginMenu(stringManager.label("context.viewport.create_obstacle", "${Icons.GEAR} "))) {
            PrefabCategory.entries.filter { it != PrefabCategory.PLAYER }.forEach { category ->
                if (ImGui.beginMenu(stringManager.getString("lbl.prefabs.${category.name.lowercase()}"))) {
                    PrefabCatalog.inCategory(category).forEach { entry ->
                        if (ImGui.menuItem(stringManager.getString(entry.type.nameKey))) {
                            eventSystem.publish(SpawnPrefab(entry.type))
                        }
                    }
                    ImGui.endMenu()
                }
            }
            ImGui.endMenu()
        }

        ImGui.separator()
    }

    private fun renderObjectManipulationMenu(scene: Scene?) {
        val selectedObject = scene?.selectedGameObject
        if (selectedObject != null) {
            if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.viewport.duplicate")}")) {
                eventSystem.publish(Duplicate(selectedObject))
            }
            if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.viewport.delete")}")) {
                scene?.let { eventSystem.publish(Delete(selectedObject, it)) }
            }
            ImGui.separator()
        }

        // Focus Selected
        if (ImGui.menuItem("${Icons.EYE} ${stringManager.getString("context.viewport.focus_selected")}")) {
            eventSystem.publish(FocusSelected)
        }

        // Reset Camera
        if (ImGui.menuItem("${Icons.ROTATE} ${stringManager.getString("context.viewport.reset_camera")}")) {
            eventSystem.publish(ResetCamera)
        }
    }
}
