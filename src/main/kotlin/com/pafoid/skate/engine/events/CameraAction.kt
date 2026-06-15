package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.render.Camera

sealed class CameraAction(eventName: String) : Event(eventName) {
    data class SetCamera(val camera: Camera) : CameraAction("camera.setCamera")
}