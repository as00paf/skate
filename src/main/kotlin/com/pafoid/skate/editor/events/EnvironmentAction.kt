package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.events.Event
import org.joml.Vector3f

sealed class EnvironmentAction(eventName: String) : Event(eventName) {
    data class SetTimeOfDayRequested(
        val scene: Scene,
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

    data class SetSunDirectionRequested(
        val lightConfig: DirectionalLightComponent,
        val oldValue: Vector3f,
        val newValue: Vector3f,
    ) : EnvironmentAction("environment.action.set_sun_direction_requested")

    data class SetSunColorRequested(
        val lightConfig: DirectionalLightComponent,
        val oldValue: Vector3f,
        val newValue: Vector3f,
    ) : EnvironmentAction("environment.action.set_sun_color_requested")

    data class SetSunIntensityRequested(
        val lightConfig: DirectionalLightComponent,
        val oldValue: Float,
        val newValue: Float,
    ) : EnvironmentAction("environment.action.set_sun_intensity_requested")

    data class SetShadowDistanceRequested(
        val lightConfig: DirectionalLightComponent,
        val oldValue: Float,
        val newValue: Float,
    ) : EnvironmentAction("environment.action.set_shadow_distance_requested")

    data class SetAutoCalculateBoundsRequested(
        val lightConfig: DirectionalLightComponent,
        val oldValue: Boolean,
        val newValue: Boolean,
    ) : EnvironmentAction("environment.action.set_auto_calculate_bounds_requested")

    data class SetStabilizeProjectionRequested(
        val lightConfig: DirectionalLightComponent,
        val oldValue: Boolean,
        val newValue: Boolean,
    ) : EnvironmentAction("environment.action.set_stabilize_projection_requested")

    data class SetDepthBiasRequested(
        val lightConfig: DirectionalLightComponent,
        val oldValue: Float,
        val newValue: Float,
    ) : EnvironmentAction("environment.action.set_depth_bias_requested")

    data class SetSlopeScaledBiasRequested(
        val lightConfig: DirectionalLightComponent,
        val oldValue: Float,
        val newValue: Float,
    ) : EnvironmentAction("environment.action.set_slope_scaled_bias_requested")

    data class SetAmbientLightRequested(
        val lightingStateComponent: LightingStateComponent,
        val oldValue: Vector3f,
        val newValue: Vector3f,
    ) : EnvironmentAction("environment.action.set_ambient_light_requested")

    data class SetAmbientIntensityRequested(
        val dayNightCycle: DayNightCycleComponent,
        val oldValue: Float,
        val newValue: Float,
    ) : EnvironmentAction("environment.action.set_ambient_intensity_requested")
}
