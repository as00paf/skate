package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import imgui.ImGui

fun DayNightCycleComponent.imgui(engine: Engine) {
    // Current phase display
    val currentPhase = getCurrentPhase(this)
    ImGui.text(engine.stringManager.getString("lbl.day_night_cycle.current_phase", currentPhase))
    ImGui.text(engine.stringManager.getString("lbl.day_night_cycle.is_daytime", this.isDaytime))

    // Time of day slider
    val cycleTimeArr = floatArrayOf(this.timeOfDay)
    if (ImGui.dragFloat(
            engine.stringManager.getString("lbl.day_night_cycle.time_of_day"),
            cycleTimeArr,
            0.1f,
            0f,
            24f,
            "%.2f"
        )
    ) {
        this.timeOfDay = cycleTimeArr[0].coerceIn(0f, 24f)
    }

    // Day duration slider
    val dayDurationArr = floatArrayOf(this.dayDuration)
    if (ImGui.dragFloat(
            engine.stringManager.getString("lbl.day_night_cycle.day_duration"),
            dayDurationArr,
            1f,
            60f,
            600f,
            "%.0f"
        )
    ) {
        this.dayDuration = dayDurationArr[0].coerceIn(60f, 600f)
    }

    timeScale =
        MImGui.sliderFloat(timeScale, engine.stringManager.getString("lbl.day_night_cycle.time_scale"), max = 10f)

    ImGui.separator()

    // Auto-ambient toggle
    val autoAmbient = this.autoAmbient
    if (ImGui.checkbox(engine.stringManager.getString("lbl.day_night_cycle.auto_ambient"), autoAmbient)) {
        this.autoAmbient = !autoAmbient
    }

    ImGui.separator()

    shadowIntensity =
        MImGui.sliderFloat(shadowIntensity, engine.stringManager.getString("lbl.day_night_cycle.shadow_intensity"))

    ImGui.separator()
    //TODO: use event system

    val noonColorArr = floatArrayOf(noonColor.x, noonColor.y, noonColor.z)
    if (MImGui.colorEdit3(engine.stringManager.getString("component.DayNightCycleComponent.noonColor"), noonColorArr)) {
        noonColor.set(noonColorArr[0], noonColorArr[1], noonColorArr[2])
    }

    val duskColorArr = floatArrayOf(duskColor.x, duskColor.y, duskColor.z)
    if (MImGui.colorEdit3(engine.stringManager.getString("component.DayNightCycleComponent.duskColor"), duskColorArr)) {
        duskColor.set(duskColorArr[0], duskColorArr[1], duskColorArr[2])
    }

    val nightColorArr = floatArrayOf(nightColor.x, nightColor.y, nightColor.z)
    if (MImGui.colorEdit3(
            engine.stringManager.getString("component.DayNightCycleComponent.nightColor"),
            nightColorArr
        )
    ) {
        nightColor.set(nightColorArr[0], nightColorArr[1], nightColorArr[2])
    }

    val dawnColorArr = floatArrayOf(dawnColor.x, dawnColor.y, dawnColor.z)
    if (MImGui.colorEdit3(engine.stringManager.getString("component.DayNightCycleComponent.dawnColor"), dawnColorArr)) {
        dawnColor.set(dawnColorArr[0], dawnColorArr[1], dawnColorArr[2])
    }

    val dayAmbientArr = floatArrayOf(dayAmbient.x, dayAmbient.y, dayAmbient.z)
    if (MImGui.colorEdit3(
            engine.stringManager.getString("component.DayNightCycleComponent.dayAmbient"),
            dayAmbientArr
        )
    ) {
        dayAmbient.set(dayAmbientArr[0], dayAmbientArr[1], dayAmbientArr[2])
    }

    val nightAmbientArr = floatArrayOf(nightAmbient.x, nightAmbient.y, nightAmbient.z)
    if (MImGui.colorEdit3(
            engine.stringManager.getString("component.DayNightCycleComponent.nightAmbient"),
            nightAmbientArr
        )
    ) {
        nightAmbient.set(nightAmbientArr[0], nightAmbientArr[1], nightAmbientArr[2])
    }
}

fun getCurrentPhase(config: DayNightCycleComponent): String { //TODO: use stringmanager
    return when (config.timeOfDay) {
        in 0f..5f -> "Night"
        in 5f..7f -> "Dawn"
        in 7f..17f -> "Day"
        in 17f..19f -> "Dusk"
        else -> "Night"
    }
}