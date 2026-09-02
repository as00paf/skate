package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform

sealed class CameraAction(eventName: String) : Event(eventName) {
    data class SetCamera(val camera: CameraComponent, val transform: Transform?) : CameraAction("camera.setCamera")
}