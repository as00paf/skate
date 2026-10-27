package com.pafoid.skate.game.player

import com.pafoid.skate.engine.gameplay.CharacterBehavior
import com.pafoid.skate.engine.gameplay.CharacterBehaviors
import com.pafoid.skate.engine.gameplay.CharacterConfigs
import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.engine.gameplay.CharacterMotion
import com.pafoid.skate.engine.gameplay.LocomotionBehavior
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.engine.gameplay.MountMath
import com.pafoid.skate.engine.gameplay.MountRequest
import com.pafoid.skate.engine.gameplay.Mountable
import com.pafoid.skate.engine.gameplay.StateMachine
import org.joml.Vector3f
import kotlin.math.abs

class SkateContext(var config: SkateControllerConfig) {
    var input = CharacterInput()
    var speed = 0f
    var grounded = true
    var airborneTime = 0f
    var pushHeld = false
    var manualHeld = false
    var jumpPressed = false
    var nearGrindable = false
    /** Facing vs travel direction in degrees, 0 when unknown. */
    var landingErrorDeg = 0f
    /** Signed manual balance in degrees; 0 = perfectly balanced. */
    var manualAngle = 0f
    var ollie = false
}

/**
 * Skateboarding behavior. While not [mounted] the character walks with the generic [LocomotionBehavior];
 * mounted, it runs the skate state machine driven by [SkateControllerConfig].
 */
class SkateBehavior(
    private val config: SkateControllerConfig,
    private val onFoot: LocomotionBehavior = LocomotionBehavior(config.onFoot),
) : CharacterBehavior, Mountable {

    /** Stub until rail detection exists (PHY): supplies whether a grindable surface is in reach. */
    var grindSensor: () -> Boolean = { false }

    private val context = SkateContext(config)
    val machine = buildMachine()

    override var mounted: Boolean = config.startMounted
        set(value) {
            if (field == value) return
            field = value
            if (value) machine.force(SkateState.IDLE, context)
        }

    /** Current manual balance in degrees (positive = nose up too far). Stick forward corrects it. */
    val manualAngle: Float get() = context.manualAngle

    override val ground get() = config.onFoot.ground

    override val state: String get() = if (mounted) machine.current.name else onFoot.state

    override val mountTargetName: String get() = config.mount.targetName
    override val mountOffset = Vector3f(0f, config.mount.offsetY, 0f)

    override fun update(input: CharacterInput, dt: Float): CharacterMotion {
        val request = MountMath.toggle(mounted, input.mountPressed, input.mountDistance, config.mount.range)
        when (request) {
            MountRequest.MOUNT -> mounted = true
            MountRequest.DISMOUNT -> mounted = false
            MountRequest.NONE -> {}
        }
        return step(input, dt).copy(mount = request)
    }

    private fun step(input: CharacterInput, dt: Float): CharacterMotion {
        if (!mounted) return onFoot.update(input, dt)

        fill(input, dt)
        context.ollie = false
        machine.update(context, dt)
        if (machine.current == SkateState.MANUAL) updateManualBalance(input, dt)
        return solve(input, dt)
    }

    private fun fill(input: CharacterInput, dt: Float) = with(context) {
        this.input = input
        speed = input.horizontalSpeed
        grounded = input.grounded
        airborneTime = if (input.grounded) 0f else airborneTime + dt
        val stick = input.move.length().let { if (it.isNaN()) 0f else it }
        pushHeld = input.sprint || (stick > config.input.moveDeadZone && input.move.y > config.input.pushStickThreshold)
        manualHeld = input.crouch
        jumpPressed = input.jumpPressed
        nearGrindable = grindSensor()
        landingErrorDeg = if (input.velocityYaw.isNaN()) 0f
        else Math.toDegrees(abs(MotionMath.wrapAngle(input.velocityYaw - input.yaw)).toDouble()).toFloat()
    }

    private fun updateManualBalance(input: CharacterInput, dt: Float) {
        val m = config.manual
        val correction = if (abs(input.move.y) > config.input.moveDeadZone) input.move.y * m.balanceGain else 0f
        val drift = if (context.manualAngle == 0f) 1f else context.manualAngle
        context.manualAngle += (drift * m.instability - correction) * dt
    }

    private fun solve(input: CharacterInput, dt: Float): CharacterMotion {
        val state = machine.current
        val ride = config.ride
        val speed = input.horizontalSpeed
        val turnRate = MotionMath.lerpClamped(speed, ride.slowSpeed, ride.fastSpeed, ride.turnRateSlow, ride.turnRateFast)
        val steered = input.yaw - input.move.x * turnRate * dt
        val impulse = if (context.ollie) Vector3f(0f, config.air.ollieImpulse, 0f) else null
        val clip = config.animations[state.name]

        fun rolling(newSpeed: Float, yaw: Float) = CharacterMotion(
            state = state.name,
            horizontalVelocity = MotionMath.directionOfYaw(yaw).mul(newSpeed),
            targetYaw = yaw,
            snapYaw = true,
            animation = clip,
        )

        return when (state) {
            SkateState.IDLE -> CharacterMotion(state.name, animation = clip, impulse = impulse)
            SkateState.PUSH -> rolling(MotionMath.approach(speed, config.push.maxSpeed, config.push.acceleration * dt), steered)
            SkateState.RIDE, SkateState.MANUAL -> {
                val braking = input.move.y < -config.input.pushStickThreshold
                val decel = if (braking) ride.brakeDecel else ride.rollingDrag
                rolling(MotionMath.approach(speed, 0f, decel * dt), steered)
            }
            SkateState.GRIND -> rolling(speed, input.yaw)
            SkateState.AIR -> CharacterMotion(
                state.name,
                targetYaw = input.yaw - input.move.x * config.air.airTurnRate * dt,
                snapYaw = true,
                impulse = impulse,
                animation = clip,
            )
            SkateState.BAIL -> rolling(MotionMath.approach(speed, 0f, config.bail.drag * dt), input.yaw)
        }
    }

    private fun buildMachine(): StateMachine<SkateState, SkateContext> {
        val onGround = listOf(SkateState.IDLE, SkateState.PUSH, SkateState.RIDE)

        return StateMachine<SkateState, SkateContext>(SkateState.IDLE).apply {
            onEnter(SkateState.AIR) { c ->
                c.ollie = c.jumpPressed && (c.grounded || previous == SkateState.GRIND)
            }
            onEnter(SkateState.MANUAL) { it.manualAngle = 0f }

            // Airborne and grind entry
            (onGround + SkateState.MANUAL).forEach { from ->
                transition(from, SkateState.AIR, 20) { it.grounded && it.jumpPressed }
                transition(from, SkateState.AIR, 10) { !it.grounded && it.airborneTime > it.config.air.coyoteTime }
            }
            listOf(SkateState.RIDE, SkateState.AIR).forEach { from ->
                transition(from, SkateState.GRIND, 15) { it.nearGrindable && it.speed >= it.config.grind.minEntrySpeed }
            }
            transition(SkateState.GRIND, SkateState.BAIL, 20) { it.speed < it.config.grind.minHoldSpeed }
            transition(SkateState.GRIND, SkateState.AIR, 10) {
                it.jumpPressed || !it.nearGrindable || timeInState >= it.config.grind.maxDurationSec
            }

            // Landing
            val landed = { c: SkateContext -> c.grounded && timeInState >= c.config.air.minAirTime }
            transition(SkateState.AIR, SkateState.BAIL, 20) { landed(it) && it.landingErrorDeg > it.config.air.landingToleranceDeg }
            transition(SkateState.AIR, SkateState.RIDE, 10) { landed(it) && it.speed >= it.config.ride.idleSpeed }
            transition(SkateState.AIR, SkateState.IDLE, 5) { landed(it) }

            // Ground movement
            transition(SkateState.IDLE, SkateState.PUSH) { it.pushHeld && it.speed < it.config.push.maxSpeed }
            transition(SkateState.IDLE, SkateState.RIDE) { it.speed >= it.config.ride.idleSpeed }
            transition(SkateState.PUSH, SkateState.RIDE) { it.speed >= it.config.push.maxSpeed || (!it.pushHeld && it.speed >= it.config.ride.idleSpeed) }
            transition(SkateState.PUSH, SkateState.IDLE) { !it.pushHeld && it.speed < it.config.ride.idleSpeed }
            transition(SkateState.RIDE, SkateState.PUSH) { it.pushHeld && it.speed < it.config.push.maxSpeed }
            transition(SkateState.RIDE, SkateState.IDLE) { !it.pushHeld && it.speed < it.config.ride.idleSpeed }

            // Manual
            transition(SkateState.RIDE, SkateState.MANUAL, 8) { it.grounded && it.manualHeld && it.speed >= it.config.manual.minSpeed }
            transition(SkateState.MANUAL, SkateState.BAIL, 20) { abs(it.manualAngle) >= it.config.manual.bailAngleDeg }
            transition(SkateState.MANUAL, SkateState.RIDE, 5) { !it.manualHeld }

            // Bail recovery
            transition(SkateState.BAIL, SkateState.IDLE) { timeInState >= it.config.bail.recoverySec }
        }
    }

    companion object {
        const val ID = "skate"
    }
}

/** Registers the game-specific behaviors with the engine. Safe to call more than once. */
object SkateGameplay {
    fun register() {
        CharacterBehaviors.register(SkateBehavior.ID) { path ->
            SkateBehavior(CharacterConfigs.load(path) { SkateControllerConfig() })
        }
    }
}
