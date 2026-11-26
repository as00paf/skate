package com.pafoid.skate.game.player

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.net.PredictionModels
import com.pafoid.skate.game.net.SkaterPredictionModel
import com.pafoid.skate.engine.animation.graph.AnimParameters
import com.pafoid.skate.engine.animation.graph.ParameterDef
import com.pafoid.skate.engine.animation.graph.ParameterType
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.gameplay.ActionPresets
import com.pafoid.skate.engine.gameplay.CameraPreset
import com.pafoid.skate.game.input.SkateActions
import com.pafoid.skate.game.input.SkateInputContext
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.systems.AppearanceSystem
import com.pafoid.skate.engine.ecs.systems.BoneAttachmentSystem
import com.pafoid.skate.engine.ecs.systems.HealthSystem
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.ecs.systems.InteractionSystem
import com.pafoid.skate.engine.gameplay.interaction.Interaction
import com.pafoid.skate.engine.ecs.systems.InventorySystem
import com.pafoid.skate.engine.gameplay.appearance.SkeletonConfigs
import com.pafoid.skate.engine.gameplay.items.ItemDatabase
import com.pafoid.skate.game.items.SkateItems
import com.pafoid.skate.game.input.SkateControls
import com.pafoid.skate.game.camera.SpotCameraSystem
import com.pafoid.skate.game.ui.ControlHints
import com.pafoid.skate.game.ui.SkateHudSystem
import com.pafoid.skate.game.input.SkateIntent
import com.pafoid.skate.game.input.SkateTrickInput
import com.pafoid.skate.game.input.TrickStyle
import org.joml.Quaternionf
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
import kotlin.math.PI
import kotlin.math.abs

class SkateContext(var config: SkateControllerConfig) {
    var input = CharacterInput()
    var speed = 0f
    /** Vertical speed in m/s, + up. */
    var verticalSpeed = 0f
    var grounded = true
    var airborneTime = 0f
    /** Fastest downward speed (m/s) since leaving the ground; the impact when touching down. */
    var fallSpeed = 0f
    /** Fall speed of the last landing, kept for the animation after the air state ends. */
    var landingImpact = 0f
    var pushHeld = false
    var manualHeld = false
    var jumpPressed = false
    var nearGrindable = false
    /** Facing vs travel direction in degrees (0..180), 0 when unknown. */
    var landingErrorDeg = 0f
    /** Rolling backward (landed a 180, GPL-23): the board travels opposite to the rider's facing. */
    var fakie = false
    /** Air turn since the pop in radians (+ = left); names the body spin on landing. */
    var spinTurn = 0f
    /** The pop started a body spin: the air turn rate is [TrickTuning.spinTurnRate]. */
    var spinning = false
    /**
     * A board trick at the pop can spin too: only when tricks are not on the steering stick (EA Skate's flick stick).
     * In the other schemes the stick direction at the pop picks the flip, so only a plain ollie spins.
     */
    var spinWithTrick = true
    var grindType = GrindType.FIFTY_FIFTY
    var manualType = ManualType.MANUAL
    /** Signed manual balance in degrees; 0 = perfectly balanced. */
    var manualAngle = 0f
    var ollie = false
    /** Steering / lean stick this frame (see [SkateIntent.move]). */
    val move = org.joml.Vector2f()
    /** Trick the control scheme asks for this frame. */
    var requestedTrick: TrickType? = null
    /** Trick in the air (null: none, e.g. rolled off a ledge) and how long it has been going. */
    var trick: TrickType? = null
    var trickTime = 0f

    /** Landing error counting a backward (fakie) landing as straight: 0..90 degrees. */
    val straightErrorDeg: Float get() = minOf(landingErrorDeg, 180f - landingErrorDeg)

    /** Board across its travel, as for a boardslide. */
    val acrossTravel: Boolean
        get() = landingErrorDeg >= config.tricks.boardslideMinDeg && landingErrorDeg <= 180f - config.tricks.boardslideMinDeg

    /** The board can lock onto the grindable edge under it now (falling or rolling onto it fast enough). */
    val canLockGrind: Boolean get() = nearGrindable && speed >= config.grind.minEntrySpeed && verticalSpeed <= 0f

    /** The board has not finished [trick] yet: touching down now is a bail. */
    val trickUnfinished: Boolean get() = trick?.let { trickTime < config.tricks.duration(it) } ?: false
}

/**
 * Skateboarding behavior. While not [mounted] the character walks with the generic [LocomotionBehavior];
 * mounted, it runs the skate state machine driven by [SkateControllerConfig].
 */
class SkateBehavior(
    val config: SkateControllerConfig,
    private val onFoot: LocomotionBehavior = LocomotionBehavior(config.onFoot),
) : CharacterBehavior, Mountable {

    private val context = SkateContext(config)
    private val trickInput = SkateTrickInput()
    val machine = buildMachine()

    /** Trick being done in the air, null when none. */
    val trick: TrickType? get() = context.trick?.takeIf { mounted && machine.current == SkateState.AIR }

    /** Body spin turned so far in the current air (null: under 90°, or no pop). */
    val airSpin: BodySpin? get() = trick?.let { BodySpin.of(context.spinTurn, config.preferredStance) }

    /** Tricks landed cleanly so far and the last one, with its spin (for the HUD). */
    var landedTricks = 0
        private set
    var lastLanded: ComboMove.Air? = null
        private set
    val lastLandedTrick: TrickType? get() = lastLanded?.trick

    /** The combo chain (GPL-23): tricks, grinds and manuals between two flat-ground landings. */
    val combo = ComboTracker { config.combo }

    /** Rolling backward after a 180. */
    val fakie: Boolean get() = context.fakie

    /** Grind or manual in progress (null: neither), named as on the HUD. */
    val balanceMove: ComboMove?
        get() = when (machine.current.takeIf { mounted }) {
            SkateState.GRIND -> ComboMove.Grind(context.grindType)
            SkateState.MANUAL -> ComboMove.Manual(context.manualType)
            else -> null
        }

    /** Manual balance from -1 to 1 (a bail at either end), null when not in a manual. */
    val manualBalance: Float?
        get() = if (mounted && machine.current == SkateState.MANUAL) {
            (context.manualAngle / config.manual.bailAngleDeg).coerceIn(-1f, 1f)
        } else null

    /** Board rotation of the trick in progress, in the board's frame ([Mountable.mountSpin]). */
    override val mountSpin: Quaternionf?
        get() = trick?.let { t ->
            val duration = config.tricks.duration(t)
            TrickSpin.of(t, if (duration > 0f) context.trickTime / duration else 1f)
        }

    override var mounted: Boolean = config.startMounted
        set(value) {
            if (field == value) return
            field = value
            if (value) machine.force(SkateState.IDLE, context)
        }

    /** Current manual balance in degrees (positive = nose up too far). Stick forward corrects it. */
    val manualAngle: Float get() = context.manualAngle

    override val look get() = config.onFoot.look

    override val state: String get() = if (mounted) machine.current.name else onFoot.state

    override val mountTargetName: String get() = config.mount.targetName
    override val cameraPreset: CameraPreset
        get() = (if (mounted) config.mount.rideCamera else config.mount.walkCamera).let { CameraPreset(it.distance, it.height) }
    override val mountOffset = Vector3f(config.mount.stanceOffsetX, config.mount.offsetY, config.mount.stanceOffsetZ)
    override val rideHeight: Float get() = config.mount.rideHeight

    override val animationBlendTimes: Map<String, Float> get() = config.blendTimes

    override val skeletonConfig: String get() = config.skeletonConfig

    /**
     * Controller parameters (floats) for blend trees and transitions: `speed` (m/s), `verticalSpeed` (m/s, + up),
     * `airTime` (s), `landImpact` (fall speed of the last landing, m/s), `manualBalance` (-1..1 toward a bail) and
     * `turn` (steering stick, -1..1).
     */
    override val animationParameters: List<ParameterDef> get() = ANIMATION_PARAMETERS

    override fun writeAnimationParameters(parameters: AnimParameters) = with(context) {
        parameters.setFloat(P_SPEED, speed)
        parameters.setFloat(P_VERTICAL_SPEED, verticalSpeed)
        parameters.setFloat(P_AIR_TIME, airborneTime)
        parameters.setFloat(P_LAND_IMPACT, landingImpact)
        parameters.setFloat(P_MANUAL_BALANCE, (manualAngle / config.manual.bailAngleDeg).coerceIn(-1f, 1f))
        parameters.setFloat(P_TURN, move.x.takeIf { it.isFinite() } ?: 0f)
    }

    /** Riding: falls off the board (BAIL) and respawns once [BailTuning.recoverySec] is over. On foot: respawns at once. */
    override fun bail(): Float {
        if (!mounted) return 0f
        machine.force(SkateState.BAIL, context)
        combo.bail()
        return config.bail.recoverySec
    }

    /** Back at the respawn point, standing still on the board (or on foot) with no trick or air time left. */
    override fun respawn() = with(context) {
        speed = 0f
        verticalSpeed = 0f
        airborneTime = 0f
        fallSpeed = 0f
        landingImpact = 0f
        manualAngle = 0f
        trick = null
        trickTime = 0f
        fakie = false
        spinTurn = 0f
        spinning = false
        combo.reset()
        if (mounted) machine.force(SkateState.IDLE, context)
        onFoot.respawn()
    }

    override fun update(input: CharacterInput, dt: Float): CharacterMotion {
        val request = MountMath.toggle(mounted, input.mountPressed, input.mountDistance, config.mount.range)
        when (request) {
            MountRequest.MOUNT -> mounted = true
            MountRequest.DISMOUNT -> mounted = false
            MountRequest.NONE -> {}
        }
        // On the board the skate context sits above gameplay: the same keys push/manual/ollie (INP-10).
        SkateInputContext.sync(input.actions, mounted)
        // The selected control scheme turns the skate actions into tricks and, for the feet schemes, steering (GPL-21).
        val scheme = SkateControls.scheme
        val tricks = trickInput.update(input.actions, input.move, scheme, dt)
        context.spinWithTrick = scheme.style == TrickStyle.FLICK_IT
        val intent = SkateIntent.read(input, config.input, if (mounted) tricks else SkateTrickInput.Frame())
        return step(input, intent, dt).copy(mount = request)
    }

    private fun step(input: CharacterInput, intent: SkateIntent, dt: Float): CharacterMotion {
        if (!mounted) return onFoot.update(input, dt)

        fill(input, intent, dt)
        context.ollie = false
        val before = machine.current
        machine.update(context, dt)
        val now = machine.current
        if (before != now) changed(before, now)
        if (before == SkateState.AIR && now == SkateState.AIR) airTrick(dt)
        if (now == SkateState.MANUAL) updateManualBalance(dt)
        combo.update(dt)
        return solve(input, dt)
    }

    /** Feeds the combo (GPL-23): a landed trick, then the grind or manual it led into, or a flat landing. */
    private fun changed(from: SkateState, to: SkateState) {
        if (from == SkateState.AIR) {
            context.landingImpact = context.fallSpeed
            land(to)
        }
        when (to) {
            SkateState.BAIL -> {
                context.fakie = false
                combo.bail()
            }
            SkateState.GRIND -> combo.add(ComboMove.Grind(context.grindType))
            SkateState.MANUAL -> combo.add(ComboMove.Manual(context.manualType))
            SkateState.IDLE, SkateState.PUSH, SkateState.RIDE ->
                if (from == SkateState.AIR || from == SkateState.MANUAL) combo.touchedFlat()
            SkateState.AIR -> {}
        }
    }

    private fun fill(input: CharacterInput, intent: SkateIntent, dt: Float) = with(context) {
        this.input = input
        speed = input.horizontalSpeed
        verticalSpeed = input.verticalSpeed
        grounded = input.grounded
        airborneTime = if (input.grounded) 0f else airborneTime + dt
        // Kept through the touchdown frame (the AIR state still decides the landing), cleared once back on the ground.
        fallSpeed = when {
            !input.grounded -> maxOf(fallSpeed, -input.verticalSpeed)
            machine.current == SkateState.AIR -> maxOf(fallSpeed, -input.verticalSpeed)
            else -> 0f
        }
        pushHeld = intent.pushHeld
        manualHeld = intent.manualHeld
        // A trick from the scheme pops like an ollie; the AIR state then knows which trick it is.
        jumpPressed = intent.olliePressed || intent.trick != null
        requestedTrick = intent.trick
        move.set(intent.move)
        nearGrindable = input.groundTag == PrefabsGenerator.GRINDABLE_TAG
        landingErrorDeg = if (input.velocityYaw.isNaN()) 0f
        else Math.toDegrees(abs(MotionMath.wrapAngle(input.velocityYaw - input.yaw)).toDouble()).toFloat()
    }

    /** In the air: the trick goes on; a flip asked after the pop (THPS, or off a ledge) replaces a plain ollie. */
    private fun airTrick(dt: Float) = with(context) {
        trickTime += dt
        val asked = requestedTrick ?: return@with
        if (asked != TrickType.OLLIE && (trick == null || trick == TrickType.OLLIE)) {
            trick = asked
            trickTime = 0f
        }
    }

    /**
     * Touchdown: a trick that was finished joins the combo with its body spin (a bail does not); the board is caught
     * either way. Landing backward on the ground rides fakie.
     */
    private fun land(to: SkateState) = with(context) {
        val done = trick
        if (done != null && to != SkateState.BAIL) {
            val move = ComboMove.Air(done, BodySpin.of(spinTurn, config.preferredStance))
            landedTricks++
            lastLanded = move
            combo.add(move)
        }
        if (to != SkateState.BAIL && to != SkateState.GRIND) fakie = landingErrorDeg > 90f
        trick = null
        trickTime = 0f
        spinTurn = 0f
        spinning = false
    }

    private fun updateManualBalance(dt: Float) {
        val m = config.manual
        val lean = context.move.y
        // A nose manual tips the other way: stick back corrects it.
        val sign = if (context.manualType == ManualType.NOSE_MANUAL) -1f else 1f
        val correction = if (abs(lean) > config.input.moveDeadZone) sign * lean * m.balanceGain else 0f
        val drift = if (context.manualAngle == 0f) 1f else context.manualAngle
        context.manualAngle += (drift * m.instability - correction) * dt
    }

    private fun solve(input: CharacterInput, dt: Float): CharacterMotion {
        val state = machine.current
        val ride = config.ride
        val speed = input.horizontalSpeed
        val turnRate = MotionMath.lerpClamped(speed, ride.slowSpeed, ride.fastSpeed, ride.turnRateSlow, ride.turnRateFast)
        val steered = input.yaw - context.move.x * turnRate * dt
        val impulse = if (context.ollie) Vector3f(0f, config.air.ollieImpulse, 0f) else null
        val clip = config.animations[state.name]

        // Fakie: the board rolls the way the rider's back faces.
        val travelOffset = if (context.fakie) PI.toFloat() else 0f
        fun rolling(newSpeed: Float, yaw: Float, travelYaw: Float = yaw + travelOffset) = CharacterMotion(
            state = state.name,
            horizontalVelocity = MotionMath.directionOfYaw(travelYaw).mul(newSpeed),
            targetYaw = yaw,
            snapYaw = true,
            animation = clip,
        )

        return when (state) {
            SkateState.IDLE -> CharacterMotion(state.name, animation = clip, impulse = impulse)
            SkateState.PUSH -> rolling(MotionMath.approach(speed, config.push.maxSpeed, config.push.acceleration * dt), steered)
            SkateState.RIDE, SkateState.MANUAL -> {
                // Stick back balances a nose manual instead of braking.
                val noseManual = state == SkateState.MANUAL && context.manualType == ManualType.NOSE_MANUAL
                val braking = !noseManual && context.move.y < -config.input.pushStickThreshold
                val decel = if (braking) ride.brakeDecel else ride.rollingDrag
                rolling(MotionMath.approach(speed, 0f, decel * dt), steered)
            }
            // Along the rail the way the board came in: a boardslide sits across its travel.
            SkateState.GRIND -> rolling(speed, input.yaw, input.velocityYaw.takeIf { it.isFinite() } ?: (input.yaw + travelOffset))
            SkateState.AIR -> {
                val rate = if (context.spinning) config.tricks.spinTurnRate else config.air.airTurnRate
                val turn = -context.move.x * rate * dt
                if (context.trick != null) context.spinTurn += turn
                CharacterMotion(state.name, targetYaw = input.yaw + turn, snapYaw = true, impulse = impulse, animation = clip)
            }
            SkateState.BAIL -> rolling(MotionMath.approach(speed, 0f, config.bail.drag * dt), input.yaw)
        }
    }

    private fun buildMachine(): StateMachine<SkateState, SkateContext> {
        val onGround = listOf(SkateState.IDLE, SkateState.PUSH, SkateState.RIDE)

        return StateMachine<SkateState, SkateContext>(SkateState.IDLE).apply {
            onEnter(SkateState.AIR) { c ->
                c.ollie = c.jumpPressed && (c.grounded || previous == SkateState.GRIND)
                c.trick = if (c.ollie) c.requestedTrick ?: TrickType.OLLIE else null
                c.trickTime = 0f
                c.spinTurn = 0f
                c.spinning = c.ollie && abs(c.move.x) > c.config.tricks.spinStickThreshold &&
                    (c.requestedTrick == null || c.spinWithTrick)
            }
            onEnter(SkateState.MANUAL) {
                it.manualAngle = 0f
                it.manualType = if (it.move.y >= it.config.tricks.leanThreshold) ManualType.NOSE_MANUAL else ManualType.MANUAL
            }
            onEnter(SkateState.GRIND) { it.grindType = GrindType.of(it.move.y, it.landingErrorDeg, it.config.tricks) }

            // Airborne and grind entry
            (onGround + SkateState.MANUAL).forEach { from ->
                transition(from, SkateState.AIR, 20) { it.grounded && it.jumpPressed }
                transition(from, SkateState.AIR, 10) { !it.grounded && it.airborneTime > it.config.air.coyoteTime }
            }
            transition(SkateState.RIDE, SkateState.GRIND, 15) { it.nearGrindable && it.speed >= it.config.grind.minEntrySpeed }
            // Only on the way down: an ollie out of a grind must not lock straight back onto the rail.
            transition(SkateState.AIR, SkateState.GRIND, 15) { it.canLockGrind }
            transition(SkateState.GRIND, SkateState.BAIL, 20) { it.speed < it.config.grind.minHoldSpeed }
            transition(SkateState.GRIND, SkateState.AIR, 10) {
                it.jumpPressed || !it.nearGrindable || timeInState >= it.config.grind.maxDurationSec
            }

            // Landing
            val landed = { c: SkateContext -> c.grounded && timeInState >= c.config.air.minAirTime }
            // Backward (fakie) counts as straight; a board across a rail locks into a boardslide instead.
            transition(SkateState.AIR, SkateState.BAIL, 20) {
                landed(it) && (it.fallSpeed >= it.config.air.bailImpactSpeed || it.trickUnfinished ||
                    (it.straightErrorDeg > it.config.air.landingToleranceDeg && !(it.canLockGrind && it.acrossTravel)))
            }
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

        const val P_SPEED = "speed"
        const val P_VERTICAL_SPEED = "verticalSpeed"
        const val P_AIR_TIME = "airTime"
        const val P_LAND_IMPACT = "landImpact"
        const val P_MANUAL_BALANCE = "manualBalance"
        const val P_TURN = "turn"

        /** Every parameter [writeAnimationParameters] writes, all floats (GPL-31). */
        val ANIMATION_PARAMETERS: List<ParameterDef> =
            listOf(P_SPEED, P_VERTICAL_SPEED, P_AIR_TIME, P_LAND_IMPACT, P_MANUAL_BALANCE, P_TURN)
                .map { ParameterDef(it, ParameterType.FLOAT) }
    }
}

/** Registers the game-specific behaviors with the engine. Safe to call more than once. */
object SkateGameplay {
    /**
     * Per-engine setup, called by each host (editor and exported game) once the engine has started: the control
     * scheme and control hint options in the pause menu, the trick and combo HUD (GPL-21, GPL-23) and the item systems (GPL-24a, e); the water bail
     * (GPL-32) is engine wiring (ECS-18). Call [register] first. Safe to call again.
     */
    fun install(engine: Engine) {
        engine.gameOptions.register(SkateControls.option())
        engine.gameOptions.register(ControlHints.option())
        // Once per engine: a second HUD would report every trick twice (GPL-25).
        if (engine.systemManager.getSystem<SpotCameraSystem>() == null) engine.systemManager.addSystem(SpotCameraSystem())
        if (engine.systemManager.getSystem<SkateHudSystem>() == null) {
            val strings = engine.gameStrings
            engine.systemManager.addSystem(SkateHudSystem(engine.eventSystem) { key, args ->
                if (args.isEmpty()) strings.getString(key) else strings.getString(key, *args)
            })
        }
        // Items, equipment, health and interaction (GPL-24a, b, d, e); the item set reloads on each scene init. One database for both systems.
        if (engine.systemManager.getSystem<InventorySystem>() == null) {
            val items = ItemDatabase()
            engine.systemManager.addSystem(
                InventorySystem(
                    engine.eventSystem, items,
                    slotsOf = SkeletonConfigs::slotsOf,
                    reload = { SkateItems.load(it, engine.assetsManager.paths.projectRoot) },
                ),
            )
            engine.systemManager.addSystem(AppearanceSystem(engine.eventSystem, items) { engine.assetsManager.loadModel(it) })
            engine.systemManager.addSystem(BoneAttachmentSystem())
            engine.systemManager.addSystem(HealthSystem(engine.eventSystem, items))
            val reader = engine.systemManager.getSystem<InputSystem>()?.reader
            engine.systemManager.addSystem(InteractionSystem(engine.eventSystem, pressed = { reader?.wasActionPressed(Interaction.ACTION) == true }))
        }
    }

    fun register() {
        ActionPresets.register(SkateActions.CATEGORY, SkateActions::register)
        ActionPresets.register(Interaction.PRESET, Interaction::bindDefaults)
        CharacterBehaviors.register(SkateBehavior.ID) { path ->
            SkateBehavior(CharacterConfigs.load(path) { SkateControllerConfig() })
        }
        if (PredictionModels[SkaterPredictionModel.ID] == null) {
            val config = CharacterConfigs.load(Assets.Configs.SKATER_CHARACTER) { SkateControllerConfig() }
            PredictionModels.register(SkaterPredictionModel.ID, SkaterPredictionModel(config))
        }
    }
}
