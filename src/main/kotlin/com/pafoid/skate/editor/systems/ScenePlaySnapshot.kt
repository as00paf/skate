package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager

/**
 * Exact pre-play state of a scene, stored as serialized scene data plus the selection (as child-index paths).
 * Resetting reloads the scene from it, so every object, component, physics body, script and animation state
 * returns to what was saved, instead of relying on per-component `reset()` hooks (which restore factory defaults).
 */
class ScenePlaySnapshot private constructor(
    private val data: String,
    private val selectionPaths: List<List<Int>>,
    /** `Scene.isDirty` is not saved (EDT-60 §7.1), so the snapshot keeps the pre-Play unsaved marker itself. */
    private val wasDirty: Boolean,
) {
    /** A fresh scene instance built from the snapshot. */
    fun decode(serializer: Serializer): Scene = serializer.decodeFile<Scene>(FileKind.SCENE, data)

    /** Resolves the saved selection against [scene]; paths that no longer exist are skipped. */
    fun selectionIn(scene: Scene): List<GameObject> = selectionPaths.mapNotNull { path ->
        var node: GameObject = scene
        for (i in path) node = node.children.getOrNull(i) ?: return@mapNotNull null
        node
    }

    /**
     * Replaces [current] in [sceneManager] by a fresh copy of this snapshot, in the same tab. It stays active if
     * [current] was. The copy keeps the captured `isDirty`: edits thrown away by the restore leave no unsaved marker.
     * Objects that still exist keep their session `uId` (matched by `guid`), so per-object editor state such as the
     * hierarchy's expanded nodes survives Stop and Reset. Returns the new scene.
     */
    fun restore(sceneManager: SceneManager, serializer: Serializer, current: Scene): Scene {
        val fresh = decode(serializer)
        fresh.isDirty = wasDirty
        keepSessionIds(current, fresh)
        sceneManager.replaceScene(current, fresh)
        fresh.setSelection(selectionIn(fresh))
        return fresh
    }

    companion object {
        fun capture(scene: Scene, serializer: Serializer): ScenePlaySnapshot {
            val paths = scene.selectedGameObjects.mapNotNull { pathOf(scene, it) }
            return ScenePlaySnapshot(serializer.encodeFile(FileKind.SCENE, scene), paths, scene.isDirty)
        }

        /** Gives each object of [fresh] the session `uId` of the object with the same `guid` in [old], if any. */
        fun keepSessionIds(old: GameObject, fresh: GameObject) {
            val byGuid = HashMap<String, Int>()
            fun collect(go: GameObject) {
                byGuid[go.guid] = go.uId
                go.children.forEach(::collect)
            }
            fun apply(go: GameObject) {
                byGuid[go.guid]?.let { if (go.uId != it) go.uId = it }
                go.children.forEach(::apply)
            }
            collect(old)
            apply(fresh)
        }

        private fun pathOf(root: GameObject, target: GameObject): List<Int>? {
            if (root === target) return emptyList()
            root.children.forEachIndexed { i, child -> pathOf(child, target)?.let { return listOf(i) + it } }
            return null
        }
    }
}
