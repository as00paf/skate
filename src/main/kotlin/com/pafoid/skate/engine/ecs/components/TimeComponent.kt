package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable

/**
 * Component containing time-related state.
 *
 * This component stores time of day and time scale settings.
 * It should be added to the Scene GameObject to control global time state.
 *
 * @property timeOfDay Current time in hours (0-24)
 * @property timeScale Time scaling factor (1.0 = normal speed, 0.0 = paused)
 */
@Serializable
class TimeComponent(
    var timeOfDay: Float = 12.0f,
    var timeScale: Float = 1.0f
) : Component() {

    /**
     * Resets all properties to default values.
     */
    fun reset() {
        timeOfDay = 12.0f
        timeScale = 1.0f
    }

    /**
     * Gets the current time formatted as HH:MM.
     * @return Time string in format "HH:MM"
     */
    fun getFormattedTime(): String {
        val hours = timeOfDay.toInt()
        val minutes = ((timeOfDay - hours) * 60).toInt()
        return String.format("%02d:%02d", hours, minutes)
    }
}
