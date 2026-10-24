package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.ComponentQuery
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import kotlin.reflect.KClass

abstract class System(
    val priority: ExecutionPriority = ExecutionPriority.DEFAULT
) {
    /** Phase this system runs in. Defaults to the phase matching [priority]. */
    open val phase: SystemPhase get() = priority.phase

    /** Systems of the same phase that must run before this one. */
    open val runsAfter: List<KClass<out System>> get() = emptyList()

    /** Systems of the same phase that must run after this one. */
    open val runsBefore: List<KClass<out System>> get() = emptyList()

    var enabled = true
    private val queries = mutableListOf<ComponentQuery>()

    open val displayName: String get() = javaClass.simpleName

    var scene: Scene = Scene()

    open fun init(scene: Scene) {
        this.scene = scene
    }

    open fun start() {}

    open fun update(dt: Float) {}

    /** Called with a constant step; only invoked for [SystemPhase.FIXED_UPDATE] systems. */
    open fun fixedUpdate(fixedDt: Float) {}

    /** Called once per frame for [SystemPhase.RENDER] systems. */
    open fun render(alpha: Float) {}

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
