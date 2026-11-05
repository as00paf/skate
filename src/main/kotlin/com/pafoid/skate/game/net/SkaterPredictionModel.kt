package com.pafoid.skate.game.net

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.net.PredictionModel
import com.pafoid.skate.game.player.SkateControllerConfig
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The skater as a [PredictionModel] (registered as [ID]). Its demo input is a player who keeps pushing to
 * [cruiseSpeed], carves a circle of [radius] meters around the origin and ollies every [ollieEveryTicks] ticks.
 */
class SkaterPredictionModel(
    config: SkateControllerConfig = SkateControllerConfig(),
    private val radius: Float = 1.8f,
    private val cruiseSpeed: Float = 3f,
    private val ollieEveryTicks: Int = 150,
) : PredictionModel {
    override val step = SkaterPredictionStep(config)

    override fun demoInput(view: GameObject, tick: Int): ByteArray {
        val t = view.getComponent<Transform>() ?: return SkaterNetInput().encode()
        val x = t.translation.x
        val z = t.translation.z
        val distance = sqrt(x * x + z * z)
        // Head along the counter-clockwise tangent, bent toward the circle when off it.
        val radial = if (distance > 1e-3f) (distance - radius) / radius else -1f
        val tangentYaw = atan2(-z, x).toDouble()
        val desiredYaw = tangentYaw - radial.coerceIn(-1f, 1f) * (Math.PI / 2.5)
        val yaw = Math.toRadians(t.rotation.y.toDouble())
        val error = Math.IEEEremainder(desiredYaw - yaw, 2 * Math.PI)
        // The step turns by -steer, so steer against the error.
        val steer = (-error * STEER_GAIN).toFloat().coerceIn(-1f, 1f)
        val speed = SkaterPredictionStep.speed(view)
        return SkaterNetInput(
            steer = steer,
            push = speed < cruiseSpeed,
            ollie = ollieEveryTicks > 0 && tick % ollieEveryTicks < OLLIE_HOLD_TICKS,
        ).encode()
    }

    companion object {
        const val ID = "skate"
        private const val STEER_GAIN = 2.0
        private const val OLLIE_HOLD_TICKS = 3
    }
}
