package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.ComponentQuery
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import kotlin.reflect.KClass

abstract class System(
    val priority: ExecutionPriority = ExecutionPriority.DEFAULT
) {
    var enabled = true
    private val queries = mutableListOf<ComponentQuery>()

    open val displayName: String get() = javaClass.simpleName

    var scene: Scene = Scene()

    open fun init(scene: Scene) {
        this.scene = scene
    }

    open fun start() {}

    open fun update(dt: Float) {}

    open fun destroy() {
        invalidateCache()
    }

    /**
     * Shared query API: entities in this system's scene having all [required] components.
     * Results refresh automatically when components/children are added or removed.
     */
    protected fun query(
        vararg required: KClass<*>,
        topLevelOnly: Boolean = false,
        includeDisabled: Boolean = false,
    ): ComponentQuery =
        ComponentQuery({ scene }, required.toList(), topLevelOnly, includeDisabled).also { queries.add(it) }

    open fun invalidateCache() {
        queries.forEach { it.invalidate() }
    }
}
