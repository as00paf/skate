package com.pafoid.skate.editor.imgui.systems

import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.systems.DayNightCycleSystem
import com.pafoid.skate.engine.getComponent
import imgui.ImGui

/**
 * Renders ImGui interface for debugging and tuning the day/night cycle.
 *
 * ## Controls
 *
 * - Time of day slider (0-24 hours)
 * - Day duration slider (60-600 seconds)
 * - Auto-ambient toggle
 * - Current phase display
 * - Sun direction, color, and intensity (read-only)
 */
fun DayNightCycleSystem.imgui(stringManager: StringManager) {
    val config = scene.getComponent<DayNightCycleComponent>() ?: return

    // Current phase display
    val currentPhase = getCurrentPhase(config)
    ImGui.text(stringManager.getString("lbl.day_night_cycle.current_phase", currentPhase))
    ImGui.text(stringManager.getString("lbl.day_night_cycle.time", config.cycleTime))

    ImGui.separator()

    // Time of day slider
    val cycleTimeArr = floatArrayOf(config.cycleTime)
    if (ImGui.dragFloat(
            stringManager.getString("lbl.day_night_cycle.time_of_day"),
            cycleTimeArr,
            0.1f,
            0f,
            24f,
            "%.2f"
        )
    ) {
        config.cycleTime = cycleTimeArr[0].coerceIn(0f, 24f)
    }

    // Day duration slider
    val dayDurationArr = floatArrayOf(config.dayDuration)
    if (ImGui.dragFloat(
            stringManager.getString("lbl.day_night_cycle.day_duration"),
            dayDurationArr,
            1f,
            60f,
            600f,
            "%.0f"
        )
    ) {
        config.dayDuration = dayDurationArr[0].coerceIn(60f, 600f)
    }

    ImGui.separator()

    // Auto-ambient toggle
    val autoAmbient = config.autoAmbient
    if (ImGui.checkbox(stringManager.getString("lbl.day_night_cycle.auto_ambient"), autoAmbient)) {
        config.autoAmbient = !autoAmbient
    }

    ImGui.separator()

    // Read-only sun properties
    ImGui.text(stringManager.getString("lbl.day_night_cycle.sun_direction"))
    ImGui.text(
        stringManager.getString(
            "lbl.day_night_cycle.sun_direction_xyz",
            config.sunDirection.x,
            config.sunDirection.y,
            config.sunDirection.z
        )
    )

    ImGui.text(stringManager.getString("lbl.day_night_cycle.sun_color"))
    ImGui.text(
        stringManager.getString(
            "lbl.day_night_cycle.sun_color_rgb",
            config.sunColor.x,
            config.sunColor.y,
            config.sunColor.z
        )
    )

    ImGui.text(stringManager.getString("lbl.day_night_cycle.sun_intensity", config.sunIntensity))

    ImGui.text(stringManager.getString("lbl.day_night_cycle.ambient_color"))
    ImGui.text(
        stringManager.getString(
            "lbl.day_night_cycle.ambient_color_rgb",
            config.ambientColor.x,
            config.ambientColor.y,
            config.ambientColor.z
        )
    )

    ImGui.text(stringManager.getString("lbl.day_night_cycle.ambient_intensity", config.ambientIntensity))

    ImGui.text(stringManager.getString("lbl.day_night_cycle.shadow_intensity", config.shadowIntensity))

    ImGui.text(stringManager.getString("lbl.day_night_cycle.is_daytime", config.isDaytime))
}


/**
 * Gets the current day phase name based on cycle time.
 * @return Phase name (Night, Dawn, Day, or Dusk)
 */
fun getCurrentPhase(config: DayNightCycleComponent): String {
    return when (config.cycleTime) {
        in 0f..5f -> "Night"
        in 5f..7f -> "Dawn"
        in 7f..17f -> "Day"
        in 17f..19f -> "Dusk"
        else -> "Night"
    }
}