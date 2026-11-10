package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.editor.imgui.tipped
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
import com.pafoid.skate.editor.events.ViewportAction.PasteClipboard
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

            renderCreateItems(scene)
            ImGui.separator()
            renderObjectManipulationMenu(scene)

            ImGui.endPopup()
        }
    }

    /**
     * The create items shared by the viewport menu and the Hierarchy's empty-space menu (EDT-52B): empty object,
     * 3D object ▸, light ▸, UI ▸, camera, obstacle ▸. New objects go to the top level of [scene].
     */
    fun renderCreateItems(scene: Scene?) {
        // Create Empty
        if (stringManager.tipped(ImGui.menuItem("${Icons.PLUS} ${stringManager.getString("context.viewport.create_empty")}", EditorShortcuts.CREATE_EMPTY.display()), "context.viewport.create_empty", EditorShortcuts.CREATE_EMPTY)) {
            scene?.let { eventSystem.publish(CreateEmpty(it)) }
        }

        // Create 3D Object submenu
        if (ImGui.beginMenu(stringManager.label("context.viewport.create_3d_object", "${Icons.CUBE} "))) {
            if (stringManager.tipped(ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.cube")), "context.viewport.create_3d_object.cube")) {
                eventSystem.publish(CreatePrimitive("Cube", PrimitiveType.CUBE, Vector3f(0.5f, 0.5f, 0.5f)))
            }
            if (stringManager.tipped(ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.sphere")), "context.viewport.create_3d_object.sphere")) {
                eventSystem.publish(CreatePrimitive("Sphere", PrimitiveType.SPHERE, Vector3f(0.5f, 0.5f, 0.5f)))
            }
            if (stringManager.tipped(ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.cylinder")), "context.viewport.create_3d_object.cylinder")) {
                eventSystem.publish(CreatePrimitive("Cylinder", PrimitiveType.CYLINDER, Vector3f(0.5f, 1f, 0.5f)))
            }
            if (stringManager.tipped(ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.plane")), "context.viewport.create_3d_object.plane")) {
                eventSystem.publish(CreatePrimitive("Plane", PrimitiveType.PLANE, Vector3f(5f, 0f, 5f)))
            }
            ImGui.endMenu()
        }

        // Create Light submenu
        if (ImGui.beginMenu(stringManager.label("context.viewport.create_light", "${Icons.SUN} "))) {
            if (stringManager.tipped(ImGui.menuItem(stringManager.getString("context.viewport.create_light.directional")), "context.viewport.create_light.directional")) {
                eventSystem.publish(CreateLight("DirectionalLight", LightType.DIRECTIONAL))
            }
            if (stringManager.tipped(ImGui.menuItem(stringManager.getString("context.viewport.create_light.point")), "context.viewport.create_light.point")) {
                eventSystem.publish(CreateLight("PointLight", LightType.POINT))
            }
            if (stringManager.tipped(ImGui.menuItem(stringManager.getString("context.viewport.create_light.spot")), "context.viewport.create_light.spot")) {
                eventSystem.publish(CreateLight("SpotLight", LightType.SPOT))
            }
            ImGui.endMenu()
        }

        UiCreateMenu.render(stringManager, eventSystem, null)

        // Create Camera
        if (stringManager.tipped(ImGui.menuItem("${Icons.CAMERA} ${stringManager.getString("context.viewport.create_camera")}"), "context.viewport.create_camera")) {
            scene?.let { eventSystem.publish(CreateCamera(it)) }
        }

        ImGui.separator()

        // One submenu per prefab picker section (PrefabCatalog), player prefabs excluded.
        if (ImGui.beginMenu(stringManager.label("context.viewport.create_obstacle", "${Icons.GEAR} "))) {
            PrefabCategory.entries.filter { it != PrefabCategory.PLAYER }.forEach { category ->
                if (ImGui.beginMenu(stringManager.getString("lbl.prefabs.${category.name.lowercase()}"))) {
                    PrefabCatalog.inCategory(category).forEach { entry ->
                        if (stringManager.tippedWith(ImGui.menuItem(stringManager.getString(entry.type.nameKey)), "context.viewport.spawn_prefab.tooltip")) {
                            eventSystem.publish(SpawnPrefab(entry.type))
                        }
                    }
                    ImGui.endMenu()
                }
            }
            ImGui.endMenu()
        }
    }

    /** Main actions first, then the destructive Delete last; items without a selection are grayed out (EDT-51 §2.3). */
    private fun renderObjectManipulationMenu(scene: Scene?) {
        val selectedObject = scene?.selectedGameObject
        val hasSelection = selectedObject != null

        if (stringManager.tipped(ImGui.menuItem("${Icons.EYE} ${stringManager.getString("context.viewport.focus_selected")}", EditorShortcuts.FOCUS.display(), false, hasSelection), "context.viewport.focus_selected", EditorShortcuts.FOCUS)) {
            eventSystem.publish(FocusSelected)
        }
        if (stringManager.tipped(ImGui.menuItem("${Icons.ARROW_ROTATE} ${stringManager.getString("context.viewport.reset_camera")}", EditorShortcuts.CAMERA_RESET.display()), "context.viewport.reset_camera", EditorShortcuts.CAMERA_RESET)) {
            eventSystem.publish(ResetCamera)
        }

        ImGui.separator()

        if (stringManager.tipped(ImGui.menuItem("${Icons.CLONE} ${stringManager.getString("context.viewport.duplicate")}", EditorShortcuts.DUPLICATE.display(), false, hasSelection), "context.viewport.duplicate", EditorShortcuts.DUPLICATE)) {
            selectedObject?.let { eventSystem.publish(Duplicate(it)) }
        }
        if (stringManager.tipped(ImGui.menuItem("${Icons.PASTE} ${stringManager.getString("context.viewport.paste")}", EditorShortcuts.PASTE.display(), false, scene != null), "context.viewport.paste", EditorShortcuts.PASTE)) {
            eventSystem.publish(PasteClipboard())
        }

        ImGui.separator()

        if (stringManager.tipped(ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.viewport.delete")}", EditorShortcuts.DELETE.display(), false, hasSelection), "context.viewport.delete", EditorShortcuts.DELETE)) {
            scene?.let { s -> selectedObject?.let { eventSystem.publish(Delete(it, s)) } }
        }
    }
}
