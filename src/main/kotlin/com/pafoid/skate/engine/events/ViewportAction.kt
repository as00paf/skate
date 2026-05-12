package com.pafoid.skate.engine.events

import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabType
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
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

object ViewportFocusSelected : ViewportAction("viewport.focus_selected")
object ViewportResetCamera : ViewportAction("viewport.reset_camera")
