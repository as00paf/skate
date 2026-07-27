package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.events.CameraAction
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f

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
        val timeScale = scene.getComponent<DayNightCycleComponent>()?.timeScale ?: 1.0f
        val scaledDt = dt * timeScale
        camera.update(scaledDt)
    }
}
