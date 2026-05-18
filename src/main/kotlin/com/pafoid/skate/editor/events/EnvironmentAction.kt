package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.ecs.components.TimeComponent

sealed class EnvironmentAction(eventName: String) : Event(eventName) {
    data class SetTimeOfDayRequested(
        val scene: Scene,
        val timeComponent: TimeComponent,
        val dayNightCycle: DayNightCycleComponent?,
        val oldTime: Float,
        val newTime: Float,
    ) : EnvironmentAction("environment.action.set_time_of_day_requested")

    data class SetUseAmbientRequested(
        val scene: Scene,
        val lightingStateComponent: LightingStateComponent,
        val oldValue: Boolean,
        val newValue: Boolean,
    ) : EnvironmentAction("environment.action.set_use_ambient_requested")

    data class SetAutoAmbientRequested(
        val dayNightCycle: DayNightCycleComponent,
        val oldValue: Boolean,
        val newValue: Boolean,
    ) : EnvironmentAction("environment.action.set_auto_ambient_requested")
}
