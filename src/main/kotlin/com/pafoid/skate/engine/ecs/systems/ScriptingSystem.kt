package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.scripting.runtime.ScriptSceneSwitcher
import com.pafoid.skate.engine.ecs.components.NativeScriptComponent
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.scripting.host.KotlinScriptHost
import com.pafoid.skate.engine.scripting.host.LuaScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.host.TypeScriptHost
import com.pafoid.skate.engine.scripting.runtime.ScriptErrorEvent
import com.pafoid.skate.engine.scripting.runtime.ScriptRuntime
import com.pafoid.skate.engine.scripting.runtime.ScriptServices

class ScriptingSystem(
    private val logger: LoggerService? = null,
    private val eventSystem: EventSystem? = null,
    services: ScriptServices = ScriptServices(),
    private val sceneManager: SceneManager? = null,
    hosts: ScriptHostRegistry = ScriptHostRegistry().apply {
        register(KotlinScriptHost())
        register(TypeScriptHost())
        register(LuaScriptHost())
    },
) : System(priority = ExecutionPriority.DEFAULT) {

    private val nativeScripts = query(NativeScriptComponent::class)
    private val sourceScripts = query(ScriptComponent::class)
    private var wasRunning = false
    private var pendingScene: Scene? = null

    private val switcher = object : ScriptSceneSwitcher {
        override val currentName: String get() = sceneManager?.currentScene?.name.orEmpty()
        override fun request(name: String): Boolean {
            val target = sceneManager?.openScenes?.firstOrNull { it.name == name } ?: return false
            pendingScene = target
            return true
        }
    }

    private val runtime = ScriptRuntime(
        hosts = hosts,
        services = ScriptServices(services.input, services.physics, if (sceneManager != null) switcher else services.scenes),
        sceneRoot = { scene },
        onError = { eventSystem?.publish(ScriptErrorEvent(it)) },
        logSink = { level, message -> logger?.log(message, level, source = "engine") },
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
            // Switching reinitializes every system (including this one), so it must not happen mid-update.
            pendingScene?.let { target ->
                pendingScene = null
                sceneManager?.switchScene(target)
            }
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
