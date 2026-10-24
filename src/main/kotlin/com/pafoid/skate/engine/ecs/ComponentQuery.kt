package com.pafoid.skate.engine.ecs

import kotlin.reflect.KClass

/**
 * Global structure version. Bumped whenever a component or child is added/removed anywhere in the ECS.
 * Queries compare against it, so no system needs its own dirty flag.
 */
object EcsChanges {
    var version: Long = 0L
        private set

    fun touch() {
        version++
    }
}

/**
 * Cached query over every descendant of [root] (pre-order, parents before children) that has all
 * [required] component types (subtypes/interfaces match). The result is rebuilt lazily when the
 * root or the ECS structure version changed; dead objects (and disabled ones, unless
 * [includeDisabled]) are filtered when reading.
 */
class ComponentQuery(
    private val root: () -> GameObject,
    private val required: List<KClass<*>>,
    private val topLevelOnly: Boolean = false,
    private val includeDisabled: Boolean = false,
) {
    private var snapshot: List<GameObject> = emptyList()
    private var builtVersion = -1L
    private var builtRoot: GameObject? = null
    private var forced = true

    var rebuildCount = 0
        private set

    fun invalidate() {
        forced = true
    }

    @PublishedApi internal fun refresh(): List<GameObject> {
        val currentRoot = root()
        if (forced || builtVersion != EcsChanges.version || builtRoot !== currentRoot) {
            val result = ArrayList<GameObject>()
            collect(currentRoot, result)
            snapshot = result
            builtVersion = EcsChanges.version
            builtRoot = currentRoot
            forced = false
            rebuildCount++
        }
        return snapshot
    }

    private fun collect(parent: GameObject, out: MutableList<GameObject>) {
        for (child in parent.children) {
            if (required.all { type -> child.components.any { type.java.isInstance(it) } }) out.add(child)
            if (!topLevelOnly) collect(child, out)
        }
    }

    @PublishedApi internal fun isLive(go: GameObject) = !go.isDead && (includeDisabled || go.isEnabled)

    /** Live matching entities. Iterates a stable snapshot: structural changes show up on the next access. */
    val entities: List<GameObject> get() = refresh().filter { isLive(it) }

    inline fun forEach(action: (GameObject) -> Unit) {
        for (go in refresh()) if (isLive(go)) action(go)
    }
}
