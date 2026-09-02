package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.NativeScriptComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getAllComponents

class ScriptingSystem : System(priority = ExecutionPriority.DEFAULT) {

    private val cache = mutableListOf<NativeScriptComponent>()

    override fun init(scene: Scene) {
        super.init(scene)
        rebuildCache()
        cacheDirty = false
    }

    override fun start() {
        cacheDirty = true
    }

    override fun update(dt: Float) {
        if (cacheDirty) rebuildCache()
        cache.forEach { script ->
            script.update(dt)
        }
    }

    override fun invalidateCache() {
        cache.clear()
        cacheDirty = true
    }

    override fun rebuildCache() {
        cache.clear()
        cache.addAll(scene.getAllComponents<NativeScriptComponent>())
    }
}