package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.events.CameraAction
import org.joml.Vector3f

/**
 * Manages the active camera used by the renderer based on engine state.
 */
class CameraManager(
    private val eventSystem: EventSystem,
) {

    var camera: Camera = Camera(Vector3f(0f, 5f, 20f))

    init {
        eventSystem.subscribe<CameraAction.SetCamera> { event ->
            camera = event.camera
        }
    }
}
