package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class DayNightCycleComponent(
    var timeOfDay: Float = 12f,
    var dayDuration: Float = 300f,
    @Contextual
    val sunDirection: Vector3f = Vector3f(0f, -1f, 0f),
    @Contextual
    val sunColor: Vector3f = Vector3f(1f, 1f, 1f),
    @Contextual
    val ambientColor: Vector3f = Vector3f(0.5f, 0.5f, 0.5f),
    var sunIntensity: Float = 1f,
    var shadowIntensity: Float = 1f,
    var isDaytime: Boolean = true,
    var ambientIntensity: Float = 1.0f,
    var autoAmbient: Boolean = true,
    var timeScale: Float = 1.0f,
) : SceneComponent() {

    fun getFormattedTime(): String {
        val hours = timeOfDay.toInt()
        val minutes = ((timeOfDay - hours) * 60).toInt()
        return String.format("%02d:%02d", hours, minutes)
    }

    fun resetComputedValues() {
        sunDirection.set(0f, -1f, 0f)
        sunColor.set(1f, 1f, 1f)
        ambientColor.set(0.5f, 0.5f, 0.5f)
        sunIntensity = 1f
        shadowIntensity = 1f
        isDaytime = true
        ambientIntensity = 1.0f
    }

    override fun reset() {
        timeOfDay = 12f
        dayDuration = 300f
        timeOfDay = 12.0f
        timeScale = 1.0f
        resetComputedValues()
    }
}