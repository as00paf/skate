package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.prefab.PrefabInstance
import com.pafoid.skate.engine.getComponent

/**
 * Turns a picking-buffer id into the object the editor selects.
 *
 * Convention (same as the Scene Hierarchy tree):
 * - The pixel id names the object that owns the mesh, at any depth ([findByUid]).
 * - The first click selects the outermost prefab-instance root above the hit, so a prefab such as
 *   a spawned skateboard is grabbed as a whole.
 * - With no prefab root, a hit object without a [RigidBody3D] whose ancestor has one selects the
 *   nearest such ancestor (the physics root), so moving it keeps the visual child on its body.
 *   The default skater and skateboard are plain objects split this way.
 * - Otherwise the hit object itself is selected.
 * - Clicking again while an object in the hit's ancestor chain is selected drills one level down
 *   toward the hit, until the hit itself is selected.
 */
object PickResolver {

    /** Depth-first lookup of [uid] among all descendants of [root]; null when no object has that id. */
    fun findByUid(root: GameObject, uid: Int): GameObject? {
        if (uid < 0) return null
        root.children.forEach { child ->
            if (child.uId == uid) return child
            findByUid(child, uid)?.let { return it }
        }
        return null
    }

    /** The selection target for a click on [hit], given the [current] selection. */
    fun selectionTarget(hit: GameObject, current: GameObject?): GameObject {
        val chain = ancestorChain(hit) // outermost first, hit last
        val index = current?.let { chain.indexOf(it) } ?: -1
        if (index >= 0) return chain[minOf(index + 1, chain.lastIndex)]
        return chain.firstOrNull { it.getComponent<PrefabInstance>() != null }
            ?: physicsRoot(hit, chain)
            ?: hit
    }

    /** Nearest ancestor with a [RigidBody3D] when [hit] has none itself; [chain] is outermost first. */
    private fun physicsRoot(hit: GameObject, chain: List<GameObject>): GameObject? {
        if (hit.getComponent<RigidBody3D>() != null) return null
        return chain.dropLast(1).lastOrNull { it.getComponent<RigidBody3D>() != null }
    }

    /** Resolves a picked [uid] in [scene] to the object to select, or null when nothing was hit. */
    fun resolve(scene: Scene, uid: Int, current: GameObject?): GameObject? =
        findByUid(scene, uid)?.let { selectionTarget(it, current) }

    private fun ancestorChain(hit: GameObject): List<GameObject> {
        return (listOf(hit) + hit.ancestors()).asReversed()
    }
}
