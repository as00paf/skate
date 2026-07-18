package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.ecs.config.ExecutionPriority
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.events.CameraAction
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f

/**
 * Manages the active camera used by the renderer based on engine state.
 */
class CameraManager(
    private val eventSystem: EventSystem,
) : System(priority = ExecutionPriority.EARLY) {

    var camera: Camera = Camera().also { it.position.set(Vector3f(0f, 5f, 20f)) }

    init {
        eventSystem.subscribe<CameraAction.SetCamera> { event ->
            camera = event.camera
        }
    }

    override fun init(scene: Scene) {
        super.init(scene)
    }

    override fun update(dt: Float) {
        val timeScale = scene.getComponent<TimeComponent>()?.timeScale ?: 1.0f
        val scaledDt = dt * timeScale
        camera.update(scaledDt)
    }
}
