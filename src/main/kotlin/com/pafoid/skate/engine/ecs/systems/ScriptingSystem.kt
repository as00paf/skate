package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.NativeScriptComponent
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.scripting.host.KotlinScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.runtime.ScriptErrorEvent
import com.pafoid.skate.engine.scripting.runtime.ScriptRuntime
import com.pafoid.skate.engine.scripting.runtime.ScriptServices

class ScriptingSystem(
    private val logger: LoggerService? = null,
    private val eventSystem: EventSystem? = null,
    services: ScriptServices = ScriptServices(),
    hosts: ScriptHostRegistry = ScriptHostRegistry().apply { register(KotlinScriptHost()) },
) : System(priority = ExecutionPriority.DEFAULT) {

    private val nativeScripts = query(NativeScriptComponent::class)
    private val sourceScripts = query(ScriptComponent::class)
    private var wasRunning = false

    private val runtime = ScriptRuntime(
        hosts = hosts,
        services = services,
        sceneRoot = { scene },
        onError = { eventSystem?.publish(ScriptErrorEvent(it)) },
        logSink = { level, message -> logger?.log(message, level, source = "script") },
    )

    override fun init(scene: Scene) {
        runtime.stop()
        wasRunning = false
        super.init(scene)
    }

    override fun update(dt: Float) {
        nativeScripts.forEach { go ->
            go.getComponent<NativeScriptComponent>()?.update(dt)
        }

        // Source scripts only run in play mode; leaving play mode destroys them.
        if (scene.isRunning) {
            val components = mutableListOf<ScriptComponent>()
            sourceScripts.forEach { go -> go.getComponent<ScriptComponent>()?.let { components.add(it) } }
            runtime.update(components, dt)
            wasRunning = true
        } else if (wasRunning) {
            runtime.stop()
            wasRunning = false
        }
    }

    override fun destroy() {
        runtime.stop()
        super.destroy()
    }
}
