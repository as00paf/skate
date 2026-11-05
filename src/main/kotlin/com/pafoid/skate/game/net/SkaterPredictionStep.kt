package com.pafoid.skate.game.net

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.net.NetState
import com.pafoid.skate.engine.net.PredictionStep
import com.pafoid.skate.engine.net.Quantize
import com.pafoid.skate.engine.net.TransformReplicator
import com.pafoid.skate.game.player.SkateControllerConfig
import com.pafoid.skate.game.player.SkateState

/** One fixed step of skater input on the wire: `steer:i8 buttons:u8`. */
data class SkaterNetInput(
    /** -1 (left) .. 1 (right), like the stick's x axis. */
    val steer: Float = 0f,
    val push: Boolean = false,
    val brake: Boolean = false,
    val ollie: Boolean = false,
) {
    fun encode(): ByteArray {
        val buttons = (if (push) PUSH else 0) or (if (brake) BRAKE else 0) or (if (ollie) OLLIE else 0)
        return byteArrayOf(Math.round(steer.coerceIn(-1f, 1f) * STEER_SCALE).toByte(), buttons.toByte())
    }

    companion object {
        const val PUSH = 1
        const val BRAKE = 2
        const val OLLIE = 4
        private const val STEER_SCALE = 127f

        /** Missing bytes read as no input, so a short or empty payload is harmless. */
        fun decode(payload: ByteArray): SkaterNetInput {
            val buttons = payload.getOrElse(1) { 0 }.toInt()
            return SkaterNetInput(
                steer = payload.getOrElse(0) { 0 } / STEER_SCALE,
                push = buttons and PUSH != 0, brake = buttons and BRAKE != 0, ollie = buttons and OLLIE != 0,
            )
        }
    }
}

/**
 * Kinematic skater for networked play (GPL-17): push, roll, brake, carve and ollie on flat ground, using the same
 * tuning as [com.pafoid.skate.game.player.SkateBehavior] but no Bullet. It reads and writes only replicated state,
 * the `Transform` (position, yaw) and the [NetState] slots below, and quantizes every value it writes to the
 * replication grid. So the server (stepping its object in place) and a client (replaying from decoded snapshots)
 * compute bit-identical states from the same inputs. Trigonometry uses `StrictMath` for the same result on every JVM.
 *
 * Ground is flat at [com.pafoid.skate.game.player.PredictionTuning.groundY]; grinds, manuals and bails need world
 * geometry and stay local to [com.pafoid.skate.game.player.SkateBehavior] for now.
 */
class SkaterPredictionStep(private val config: SkateControllerConfig = SkateControllerConfig()) : PredictionStep {

    override fun step(go: GameObject, input: ByteArray, dt: Float) {
        val transform = go.getComponent<Transform>() ?: return
        val values = go.getComponent<NetState>()?.values ?: return
        val cmd = SkaterNetInput.decode(input)
        val tuning = config.prediction
        val ride = config.ride
        val pos = transform.translation

        var speed = values[SPEED] / SPEED_SCALE
        var verticalSpeed = values[VERTICAL_SPEED] / SPEED_SCALE
        var state = SkateState.entries.getOrElse(values[STATE]) { SkateState.IDLE }
        var airTicks = values[AIR_TICKS]
        val ollieWasHeld = values[BUTTONS] and SkaterNetInput.OLLIE != 0
        val grounded = state != SkateState.AIR

        val turnRate = if (grounded) MotionMath.lerpClamped(speed, ride.slowSpeed, ride.fastSpeed, ride.turnRateSlow, ride.turnRateFast)
            else config.air.airTurnRate
        val yaw = Math.toRadians(transform.rotation.y.toDouble()) - cmd.steer * turnRate * dt

        if (grounded && cmd.ollie && !ollieWasHeld) {
            verticalSpeed = config.air.ollieImpulse / tuning.mass
            state = SkateState.AIR
            airTicks = 0
        } else if (grounded) {
            speed = when {
                cmd.push -> MotionMath.approach(speed, config.push.maxSpeed, config.push.acceleration * dt)
                cmd.brake -> MotionMath.approach(speed, 0f, ride.brakeDecel * dt)
                else -> MotionMath.approach(speed, 0f, ride.rollingDrag * dt)
            }
            state = groundState(speed, cmd.push)
        } else {
            verticalSpeed -= tuning.gravity * dt
            airTicks++
        }

        var x = pos.x + (StrictMath.sin(yaw) * speed * dt).toFloat()
        var y = pos.y + verticalSpeed * dt
        var z = pos.z + (StrictMath.cos(yaw) * speed * dt).toFloat()
        if (state == SkateState.AIR && y <= tuning.groundY && verticalSpeed <= 0f) {
            y = tuning.groundY
            verticalSpeed = 0f
            state = groundState(speed, cmd.push)
        }
        if (state != SkateState.AIR) y = tuning.groundY

        pos.set(onGrid(x), onGrid(y), onGrid(z))
        transform.rotation.y = Quantize.toFloat(Quantize.toInt(wrapDegrees(Math.toDegrees(yaw)), TransformReplicator.ROT_SCALE), TransformReplicator.ROT_SCALE)
        values[SPEED] = Quantize.toInt(speed, SPEED_SCALE)
        values[VERTICAL_SPEED] = Quantize.toInt(verticalSpeed, SPEED_SCALE)
        values[STATE] = state.ordinal
        values[AIR_TICKS] = airTicks
        values[BUTTONS] = input.getOrElse(1) { 0 }.toInt() and 0xFF
    }

    private fun groundState(speed: Float, push: Boolean) = when {
        push && speed < config.push.maxSpeed -> SkateState.PUSH
        speed >= config.ride.idleSpeed -> SkateState.RIDE
        else -> SkateState.IDLE
    }

    private fun onGrid(v: Float) = Quantize.toFloat(Quantize.toInt(v, TransformReplicator.POS_SCALE), TransformReplicator.POS_SCALE)

    private fun wrapDegrees(deg: Double): Float {
        val wrapped = deg % 360.0
        return (if (wrapped > 180.0) wrapped - 360.0 else if (wrapped <= -180.0) wrapped + 360.0 else wrapped).toFloat()
    }

    companion object {
        /** [NetState] slots: speeds in 1/1024 m/s, state = [SkateState] ordinal, air time in ticks, last buttons. */
        const val SPEED = 0
        const val VERTICAL_SPEED = 1
        const val STATE = 2
        const val AIR_TICKS = 3
        const val BUTTONS = 4
        const val SPEED_SCALE = 1024f

        fun state(go: GameObject): SkateState = SkateState.entries[go.getComponent<NetState>()!!.values[STATE]]
        fun speed(go: GameObject): Float = go.getComponent<NetState>()!!.values[SPEED] / SPEED_SCALE
    }
}
