package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority

abstract class System(
    val priority: ExecutionPriority = ExecutionPriority.DEFAULT
) {
    var enabled = true
    var cacheDirty = false

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

    open fun invalidateCache() {}

    open fun rebuildCache() {}
}
