package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.scenes.components.Component
import org.joml.Quaternionf

class BoneOverride : Component() {
    private val overrides = mutableMapOf<String, Quaternionf>()

    fun addOverride(boneName: String, rotation: Quaternionf) {
        overrides[boneName] = rotation
    }

    fun getOverride(boneName: String): Quaternionf? {
        return overrides[boneName]
    }

    fun clearOverrides() {
        overrides.clear()
    }
}
