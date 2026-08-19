package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.data.PrefabType
import com.pafoid.skate.editor.data.PrimitiveType
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.render.data.LightType
import org.joml.Vector3f

sealed class ViewportAction(eventName: String) : Event(eventName) {
    // Game Objects
    data class CreateEmpty(val scene: Scene) : ViewportAction("viewport.create_empty")
    data class CreatePrimitive(val name: String, val type: PrimitiveType, val halfExtents: Vector3f) :
        ViewportAction("viewport.create_primitive")
    data class CreateEmptyChild(val parent: GameObject) : ViewportAction("viewport.create_empty_child")
    data class AddComponent(val gameObject: GameObject, val componentType: ComponentType) : ViewportAction("viewport.add_component")
    data class RemoveComponent(val component: Component) :
        ViewportAction("viewport.remove_component")
    data class Duplicate(val gameObject: GameObject) : ViewportAction("viewport.duplicate")
    data class Delete(val gameObject: GameObject, val scene: Scene) : ViewportAction("viewport.delete")
    data class RenameGameObject(val gameObject: GameObject, val newName: String) : ViewportAction("viewport.rename_gameobject")
    data class Reparent(val child: GameObject, val newParent: GameObject) : ViewportAction("viewport.reparent")
    data class SetGameObjectEnabled(val gameObject: GameObject, val enabled: Boolean) : ViewportAction("viewport.set_gameobject_enabled")
    data class ToggleVisibility(val gameObject: GameObject, val visible: Boolean) : ViewportAction("viewport.toggle_visibility")
    data class ToggleLock(val gameObject: GameObject, val locked: Boolean) : ViewportAction("viewport.toggle_lock")

    data class RenameComponent(val component: Component, val newName: String) :
        ViewportAction("viewport.rename_component")

    data class SetComponentEnabled(val component: Component, val enabled: Boolean) :
        ViewportAction("viewport.set_component_enabled")
    // TODO : use data classes
    data class TextureApplied(val gameObject: GameObject, val texturePath: String) : ViewportAction("editor.texture_applied")

    // Light & Camera
    data class CreateLight(val name: String, val type: LightType) : ViewportAction("viewport.create_light")
    data class CreateCamera(val scene: Scene) : ViewportAction("viewport.create_camera")
    object FocusSelected : ViewportAction("viewport.focus_selected")
    object ResetCamera : ViewportAction("viewport.reset_camera")

    // Prefabs
    data class SpawnPrefab(val prefabType: PrefabType, val position: Vector3f? = null) : ViewportAction("viewport.spawn_prefab")

    // Drag and Drop
    data class DropTexture(val texturePath: String, val targetObject: GameObject? = null, val dropPosition: Vector3f? = null) : ViewportAction("viewport.drop_texture")
    data class DropSound(val soundPath: String, val targetObject: GameObject) : ViewportAction("viewport.drop_sound")
    data class ApplyAnimation(val animation: Animation, val targetObject: GameObject) :
        ViewportAction("viewport.apply_animation")

    // Runtime
    data class SetSimulationTimeScale(val timeScale: Float) : ViewportAction("viewport.set_simulation_timescale")
    data class ResetTransform(val gameObject: GameObject) : ViewportAction("viewport.reset_transform")

    // Gizmos
    data class TabSelected(val scene: Scene) : ViewportAction("scene.action.tab_selected")
    data class ToggleGizmo(val gizmoId: Int) : ViewportAction("viewport.toggle_gizmo")
    object TogglePhysicsDebug : ViewportAction("viewport.toggle_physics_debug")
    object ScreenshotRequested : ViewportAction("viewport.screenshot_requested")

    // Clipboard
    data class CutClipboard(val gameObject: GameObject) : ViewportAction("viewport.cut_clipboard")
    data class CopyClipboard(val gameObject: GameObject) : ViewportAction("viewport.copy_clipboard")
    data class PasteClipboard(val parent: GameObject? = null) : ViewportAction("viewport.paste_clipboard")

    // Selection
    data class GameObjectSelected(val gameObject: GameObject) : ViewportAction("editor.gameobject_selected")
    data class MultiSelectionChanged(val selectedObjects: List<GameObject>) : ViewportAction("editor.multi_selection_changed")
    object SelectionCleared : ViewportAction("editor.selection_cleared")

    // Windows
    object ToggleFullScreen : ViewportAction("editor.toggle_fullscreen")

}
