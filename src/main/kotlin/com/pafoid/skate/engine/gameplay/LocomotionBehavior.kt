package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.gameplay.ground.GroundConfig
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/** Tuning for generic on-foot movement. Stored in a standalone config file, never in code. */
@Serializable
data class LocomotionConfig(
    val walkSpeed: Float = 2.5f,
    val runSpeed: Float = 7.5f,
    val acceleration: Float = 20f,
    val rotationSpeed: Float = 10f,
    /** Stick magnitude below which input is ignored. */
    val moveDeadZone: Float = 0.15f,
    /** Stick magnitude above which the character runs without sprint held. */
    val runInputThreshold: Float = 0.65f,
    val jumpImpulse: Float = 300f,
    /** Wind-up between pressing jump and leaving the ground. */
    val takeOffTime: Float = 0.9f,
    /** Grace time after leaving the ground before the character counts as airborne. */
    val coyoteTime: Float = 0.1f,
    /** Minimum time airborne before landing can be detected (ground probe lags behind the impulse). */
    val minAirTime: Float = 0.15f,
    /** Fraction of ground acceleration available while airborne. 0 disables air control. */
    val airControl: Float = 0.2f,
    /** Ground detection, slopes, steps and snapping. Also used by behaviors that wrap this config. */
    val ground: GroundConfig = GroundConfig(),
    /** Animation clip per [LocomotionState] name. */
    val animations: Map<String, String> = mapOf(
        "IDLE" to "idle",
        "WALK" to "walking",
        "RUN" to "running",
        "TAKEOFF" to "jump",
        "AIR" to "falling idle",
    ),
)

enum class LocomotionState { IDLE, WALK, RUN, TAKEOFF, AIR }

class LocomotionContext {
    var input = CharacterInput()
    var moveMagnitude = 0f
    var airborneTime = 0f
    var config = LocomotionConfig()
}

/** Generic on-foot controller: idle, walk, run and jump with a wind-up. */
/** Open so games can extend it (override [update] and call super for the walking part). */
open class LocomotionBehavior(private val config: LocomotionConfig) : CharacterBehavior {

    override val ground: GroundConfig get() = config.ground

    private val context = LocomotionContext().also { it.config = config }
    private val direction = Vector3f()
    private var speed = 0f

    val machine = buildMachine()

    override val state: String get() = machine.current.name

    override fun update(input: CharacterInput, dt: Float): CharacterMotion {
        context.input = input
        context.moveMagnitude = input.move.length().let { if (it.isNaN()) 0f else it }
        context.airborneTime = if (input.grounded) 0f else context.airborneTime + dt

        val changed = machine.update(context, dt)
        val current = machine.current
        val impulse = if (changed && machine.previous == LocomotionState.TAKEOFF && current == LocomotionState.AIR) {
            Vector3f(0f, config.jumpImpulse, 0f)
        } else null

        val hasInput = context.moveMagnitude > config.moveDeadZone
        val target = when (current) {
            LocomotionState.RUN -> config.runSpeed
            LocomotionState.WALK -> config.walkSpeed
            LocomotionState.TAKEOFF, LocomotionState.AIR -> if (isRunning(input)) config.runSpeed else config.walkSpeed
            LocomotionState.IDLE -> 0f
        }
        val accel = config.acceleration * if (input.grounded) 1f else config.airControl
        val controllable = hasInput && accel > 0f && current != LocomotionState.IDLE

        var velocity: Vector3f? = null
        var targetYaw = Float.NaN
        if (controllable) {
            MotionMath.cameraRelativeDirection(input.cameraForward, input.cameraRight, input.move, direction)
            if (direction.lengthSquared() > 0f) {
                speed = MotionMath.approach(speed, target, accel * dt)
                velocity = Vector3f(direction).mul(speed)
                targetYaw = MotionMath.yawOfDirection(direction)
            }
        } else if (input.grounded) {
            speed = 0f
        }

        return CharacterMotion(
            state = current.name,
            horizontalVelocity = velocity,
            targetYaw = targetYaw,
            yawRate = config.rotationSpeed,
            impulse = impulse,
            animation = config.animations[current.name],
        )
    }

    private fun isRunning(input: CharacterInput) =
        input.sprint || context.moveMagnitude > config.runInputThreshold

    private fun buildMachine(): StateMachine<LocomotionState, LocomotionContext> {
        val grounded = { c: LocomotionContext -> c.input.grounded }
        val hasInput = { c: LocomotionContext -> c.moveMagnitude > c.config.moveDeadZone }
        val wantsRun = { c: LocomotionContext -> hasInput(c) && (c.input.sprint || c.moveMagnitude > c.config.runInputThreshold) }
        val ground = listOf(LocomotionState.IDLE, LocomotionState.WALK, LocomotionState.RUN)

        return StateMachine<LocomotionState, LocomotionContext>(LocomotionState.IDLE).apply {
            ground.forEach { from ->
                transition(from, LocomotionState.TAKEOFF, priority = 20) { grounded(it) && it.input.jumpPressed }
                transition(from, LocomotionState.AIR, priority = 10) { !grounded(it) && it.airborneTime > it.config.coyoteTime }
            }
            transition(LocomotionState.IDLE, LocomotionState.RUN) { wantsRun(it) }
            transition(LocomotionState.IDLE, LocomotionState.WALK) { hasInput(it) }
            transition(LocomotionState.WALK, LocomotionState.RUN) { wantsRun(it) }
            transition(LocomotionState.WALK, LocomotionState.IDLE) { !hasInput(it) }
            transition(LocomotionState.RUN, LocomotionState.WALK) { hasInput(it) && !wantsRun(it) }
            transition(LocomotionState.RUN, LocomotionState.IDLE) { !hasInput(it) }
            transition(LocomotionState.TAKEOFF, LocomotionState.AIR) { takeOffDone(it) }
            transition(LocomotionState.AIR, LocomotionState.IDLE) { grounded(it) && !hasInput(it) && landed(it) }
            transition(LocomotionState.AIR, LocomotionState.WALK) { grounded(it) && hasInput(it) && !wantsRun(it) && landed(it) }
            transition(LocomotionState.AIR, LocomotionState.RUN) { grounded(it) && wantsRun(it) && landed(it) }
        }
    }

    private fun takeOffDone(c: LocomotionContext) = machine.timeInState >= c.config.takeOffTime

    private fun landed(c: LocomotionContext) = machine.timeInState >= c.config.minAirTime

    companion object {
        const val ID = "locomotion"
    }
}
