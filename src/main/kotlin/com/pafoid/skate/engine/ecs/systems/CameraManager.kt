package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.events.CameraAction
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f

class CameraManager(
    eventSystem: EventSystem,
) : System(priority = SystemManager.ExecutionPriority.EARLY) {

    var camera: CameraComponent = CameraComponent().also { it.position.set(Vector3f(0f, 5f, 20f)) }

    init {
        eventSystem.subscribe<CameraAction.SetCamera> { event ->
            camera = event.camera
        }
    }

    override fun update(dt: Float) {
        val timeScale = scene.getComponent<DayNightCycleComponent>()?.timeScale ?: 1.0f
        val scaledDt = dt * timeScale
        camera.update(scaledDt)
    }
}