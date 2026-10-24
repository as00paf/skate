package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.NativeScriptComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent

class ScriptingSystem : System(priority = ExecutionPriority.DEFAULT) {

    private val scripts = query(NativeScriptComponent::class)

    override fun update(dt: Float) {
        scripts.forEach { go ->
            go.getComponent<NativeScriptComponent>()?.update(dt)
        }
    }
}
