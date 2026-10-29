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
     * Replaces [current] in [sceneManager] by a fresh copy of this snapshot at the same position and makes it active.
     * Returns the new scene.
     */
    fun restore(sceneManager: SceneManager, serializer: Serializer, current: Scene): Scene {
        val index = sceneManager.openScenes.indexOf(current)
        val fresh = decode(serializer)
        sceneManager.closeScene(current)
        sceneManager.openScene(fresh)
        if (index in 0 until sceneManager.openScenes.size - 1) {
            sceneManager.openScenes.remove(fresh)
            sceneManager.openScenes.add(index, fresh)
            sceneManager.switchScene(fresh)
        }
        fresh.setSelection(selectionIn(fresh))
        fresh.isDirty = current.isDirty
        return fresh
    }

    companion object {
        fun capture(scene: Scene, serializer: Serializer): ScenePlaySnapshot {
            val paths = scene.selectedGameObjects.mapNotNull { pathOf(scene, it) }
            return ScenePlaySnapshot(serializer.encodeFile(FileKind.SCENE, scene), paths)
        }

        private fun pathOf(root: GameObject, target: GameObject): List<Int>? {
            if (root === target) return emptyList()
            root.children.forEachIndexed { i, child -> pathOf(child, target)?.let { return listOf(i) + it } }
            return null
        }
    }
}
