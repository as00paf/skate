package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabType
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.render.data.LightType
import org.joml.Vector3f

sealed class ViewportAction(eventName: String) : Event(eventName)

data class ViewportCreateEmpty(val scene: Scene) : ViewportAction("viewport.create_empty")
data class ViewportCreatePrimitive(val name: String, val halfExtents: Vector3f) : ViewportAction("viewport.create_primitive")
data class ViewportCreateLight(val name: String, val type: LightType) : ViewportAction("viewport.create_light")
data class ViewportCreateCamera(val scene: Scene) : ViewportAction("viewport.create_camera")

data class ViewportSpawnPrefab(
    val prefabType: PrefabType,
    val position: Vector3f? = null
) : ViewportAction("viewport.spawn_prefab")

data class ViewportDropTexture(
    val texturePath: String,
    val targetObject: GameObject? = null,
    val dropPosition: Vector3f? = null
) : ViewportAction("viewport.drop_texture")

data class ViewportDropSound(
    val soundPath: String,
    val targetObject: GameObject
) : ViewportAction("viewport.drop_sound")

data class ViewportDropAnimation(
    val animationPath: String,
    val targetObject: GameObject
) : ViewportAction("viewport.drop_animation")

data class ViewportDuplicate(val gameObject: GameObject) : ViewportAction("viewport.duplicate")
data class ViewportDelete(val gameObject: GameObject, val scene: Scene) : ViewportAction("viewport.delete")
data class ViewportRenameGameObject(val gameObject: GameObject, val newName: String) : ViewportAction("viewport.rename_gameobject")
data class ViewportSetGameObjectEnabled(val gameObject: GameObject, val enabled: Boolean) : ViewportAction("viewport.set_gameobject_enabled")
data class ViewportAddComponent(val gameObject: GameObject, val componentType: ComponentType) : ViewportAction("viewport.add_component")
data class ViewportRemoveComponent(val gameObject: GameObject, val componentType: ComponentType) : ViewportAction("viewport.remove_component")
data class ViewportToggleVisibility(val gameObject: GameObject, val visible: Boolean) : ViewportAction("viewport.toggle_visibility")
data class ViewportToggleLock(val gameObject: GameObject, val locked: Boolean) : ViewportAction("viewport.toggle_lock")
data class ViewportReparent(val child: GameObject, val newParent: GameObject) : ViewportAction("viewport.reparent")
data class ViewportCreateEmptyChild(val parent: GameObject) : ViewportAction("viewport.create_empty_child")
data class ViewportPasteClipboard(val parent: GameObject? = null) : ViewportAction("viewport.paste_clipboard")
data class ViewportSetRuntimePlaying(val playing: Boolean) : ViewportAction("viewport.set_runtime_playing")
data class ViewportSetSimulationTimeScale(val timeScale: Float) : ViewportAction("viewport.set_simulation_timescale")
data class ViewportResetTransform(val gameObject: GameObject) : ViewportAction("viewport.reset_transform")
object ViewportResetScene : ViewportAction("viewport.reset_skate_scene")
object ViewportTogglePhysicsDebug : ViewportAction("viewport.toggle_physics_debug")
data class ViewportToggleGizmo(val gizmoId: Int) : ViewportAction("viewport.toggle_gizmo")

object ViewportFocusSelected : ViewportAction("viewport.focus_selected")
object ViewportResetCamera : ViewportAction("viewport.reset_camera")
