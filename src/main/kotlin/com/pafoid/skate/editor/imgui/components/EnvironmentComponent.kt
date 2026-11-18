package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.events.ComponentFieldAction
import com.pafoid.skate.editor.imgui.EnumLabels
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.environment.WindField
import imgui.ImGui
import kotlin.math.abs

/** Wind presets of the Environment inspector (EDT-90): the speeds of [WindField]'s calm, breeze, strong and storm. */
enum class WindPreset(val speed: Float) {
    CALM(WindField.CALM),
    BREEZE(WindField.BREEZE),
    STRONG(WindField.STRONG),
    STORM(WindField.STORM);

    companion object {
        /** The preset whose speed [speed] is, or null for a custom speed. */
        fun of(speed: Float): WindPreset? = entries.firstOrNull { abs(it.speed - speed) < 1e-3f }
    }
}

private const val KEY = "component.EnvironmentComponent"
private val WIND_FIELDS = setOf(
    "windDirection", "windSpeed", "windGustStrength", "windGustScale", "windTurbulence", "animateInEditor", "windCycleButton",
)
private val WIND_SPEED = EnvironmentComponent::class.java.getDeclaredField("windSpeed").apply { isAccessible = true }

/**
 * Environment inspector: the generic rows, with the wind (REN-29 §3.1) gathered in a Wind section that starts with
 * preset buttons (EDT-90). A preset sets the wind speed through an undoable field command; the other rows go through
 * the shared edit tracker.
 */
fun EnvironmentComponent.imgui(engine: Engine) {
    val all = Component.getCachedFields(javaClass).map { it.name }.toSet()
    reflectedImgui(engine, skip = WIND_FIELDS)

    val strings = engine.stringManager
    MImGui.labeledSeparator(strings.getString("$KEY.wind"))
    Tooltips.showIfPresent(strings, "$KEY.wind${Tooltips.SUFFIX}")
    ImGui.pushID("$KEY.windPreset")
    val active = WindPreset.of(windSpeed)
    WindPreset.entries.forEachIndexed { i, preset ->
        if (i > 0) ImGui.sameLine()
        val selected = preset == active
        if (selected) ImGui.beginDisabled()
        val clicked = ImGui.button(EnumLabels.of(strings, preset))
        if (selected) ImGui.endDisabled()
        if (strings.tippedWith(clicked, "$KEY.windPreset${Tooltips.SUFFIX}") && !selected) {
            engine.eventSystem.publish(ComponentFieldAction.Set(this, WIND_SPEED, preset.speed))
        }
    }
    ImGui.popID()
    reflectedImgui(engine, skip = all - WIND_FIELDS)
}
