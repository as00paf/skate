package com.pafoid.skate.engine.scenes.components

import org.joml.Vector3f

class CloudDrift(
    var speed: Float = 0.5f,
    var driftDirection: Vector3f = Vector3f(1f, 0f, 0f),
    var resetX: Float = 500f,
    var startX: Float = -500f,
    var isBillboard: Boolean = true
) : Component() {

    override fun update(dt: Float) {
        val translation = gameObject.transform.translation
        translation.add(Vector3f(driftDirection).mul(speed * dt))

        if (translation.x > resetX) {
            translation.x = startX
        } else if (translation.x < startX) {
            translation.x = resetX
        }

        if (isBillboard) {
            val camera = com.pafoid.skate.engine.scenes.SceneManager.getCurrentScene()?.camera
            if (camera != null) {
                gameObject.transform.rotation.y = -camera.yaw
                gameObject.transform.rotation.x = -camera.pitch
            }
        }
    }
}
