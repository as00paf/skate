package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.render.CameraComponent

sealed class CameraAction(eventName: String) : Event(eventName) {
    data class SetCamera(val camera: CameraComponent) : CameraAction("camera.setCamera")
}