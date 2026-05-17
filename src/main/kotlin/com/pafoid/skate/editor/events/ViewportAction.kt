package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabType
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.render.data.LightType
import org.joml.Vector3f

sealed class ViewportAction(eventName: String) : Event(eventName) {

    data class CreateEmpty(val scene: Scene) : ViewportAction("viewport.create_empty")
    data class CreatePrimitive(val name: String, val halfExtents: Vector3f) : ViewportAction("viewport.create_primitive")
    data class CreateLight(val name: String, val type: LightType) : ViewportAction("viewport.create_light")
    data class CreateCamera(val scene: Scene) : ViewportAction("viewport.create_camera")
    data class SpawnPrefab(val prefabType: PrefabType, val position: Vector3f? = null) : ViewportAction("viewport.spawn_prefab")
    data class DropTexture(val texturePath: String, val targetObject: GameObject? = null, val dropPosition: Vector3f? = null) : ViewportAction("viewport.drop_texture")
    data class DropSound(val soundPath: String, val targetObject: GameObject) : ViewportAction("viewport.drop_sound")
    data class DropAnimation(val animationPath: String, val targetObject: GameObject) : ViewportAction("viewport.drop_animation")
    data class Duplicate(val gameObject: GameObject) : ViewportAction("viewport.duplicate")
    data class Delete(val gameObject: GameObject, val scene: Scene) : ViewportAction("viewport.delete")
    data class RenameGameObject(val gameObject: GameObject, val newName: String) : ViewportAction("viewport.rename_gameobject")
    data class SetGameObjectEnabled(val gameObject: GameObject, val enabled: Boolean) : ViewportAction("viewport.set_gameobject_enabled")
    data class AddComponent(val gameObject: GameObject, val componentType: ComponentType) : ViewportAction("viewport.add_component")
    data class RemoveComponent(val gameObject: GameObject, val componentType: ComponentType) : ViewportAction("viewport.remove_component")
    data class ToggleVisibility(val gameObject: GameObject, val visible: Boolean) : ViewportAction("viewport.toggle_visibility")
    data class ToggleLock(val gameObject: GameObject, val locked: Boolean) : ViewportAction("viewport.toggle_lock")
    data class Reparent(val child: GameObject, val newParent: GameObject) : ViewportAction("viewport.reparent")
    data class CreateEmptyChild(val parent: GameObject) : ViewportAction("viewport.create_empty_child")
    data class PasteClipboard(val parent: GameObject? = null) : ViewportAction("viewport.paste_clipboard")
    data class SetRuntimePlaying(val playing: Boolean) : ViewportAction("viewport.set_runtime_playing")
    data class SetSimulationTimeScale(val timeScale: Float) : ViewportAction("viewport.set_simulation_timescale")
    data class ResetTransform(val gameObject: GameObject) : ViewportAction("viewport.reset_transform")
    object ResetScene : ViewportAction("viewport.reset_skate_scene")
    object TogglePhysicsDebug : ViewportAction("viewport.toggle_physics_debug")
    data class ToggleGizmo(val gizmoId: Int) : ViewportAction("viewport.toggle_gizmo")

    object FocusSelected : ViewportAction("viewport.focus_selected")
    object ResetCamera : ViewportAction("viewport.reset_camera")

}


