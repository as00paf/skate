package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f

@Serializable
data class DayNightCycleComponent(
    var timeOfDay: Float = 12f,
    var dayDuration: Float = 300f,
    var shadowIntensity: Float = 1f,
    var autoAmbient: Boolean = true,
    var timeScale: Float = 1.0f,
    @Contextual val noonColor: Vector3f = Vector3f(1.0f, 0.95f, 0.8f),  // Warm sunlight
    @Contextual val duskColor: Vector3f = Vector3f(1.0f, 0.6f, 0.3f),   // Orange sunset
    @Contextual val nightColor: Vector3f = Vector3f(0.3f, 0.4f, 0.6f),  // Cool moonlight
    @Contextual val dawnColor: Vector3f = Vector3f(1.0f, 0.7f, 0.5f),   // Pink/orange dawn
    @Contextual val nightAmbient: Vector3f = Vector3f(0.05f, 0.05f, 0.1f),
    @Contextual val dayAmbient: Vector3f = Vector3f(0.3f, 0.3f, 0.35f),
) : SceneComponent() {

    @Transient
    var isDaytime: Boolean = true

    fun getFormattedTime(): String {
        val hours = timeOfDay.toInt()
        val minutes = ((timeOfDay - hours) * 60).toInt()
        return String.format("%02d:%02d", hours, minutes)
    }

    fun resetComputedValues() {
        shadowIntensity = 1f
        isDaytime = true
    }

    override fun reset() {
        timeOfDay = 12f
        dayDuration = 300f
        timeOfDay = 12.0f
        timeScale = 1.0f
        resetComputedValues()
    }
}