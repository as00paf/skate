package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.scenes.components.Component
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Quaternionf

@Serializable
class BoneOverride : Component() {
    private val overrides:MutableMap<String, @Contextual Quaternionf> = mutableMapOf()

    fun addOverride(boneName: String, rotation: Quaternionf) {
        overrides[boneName] = rotation
    }

    fun getOverride(boneName: String): Quaternionf? {
        return overrides[boneName]
    }

    fun getOverrides(): Map<String, Quaternionf> {
        return overrides.toMap()
    }

    fun clearOverrides() {
        overrides.clear()
    }
}
