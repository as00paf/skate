package com.pafoid.skate.game.player

import com.pafoid.skate.engine.gameplay.LocomotionConfig
import kotlinx.serialization.Serializable

enum class SkateState { IDLE, PUSH, RIDE, AIR, GRIND, MANUAL, BAIL }

/** All skate tuning. Lives in a standalone `*.character.json` file referenced by the PlayerController. */
@Serializable
data class SkateControllerConfig(
    /** Start on the board. When false the character walks (on-foot [onFoot] config) until mounted. */
    val startMounted: Boolean = false,
    val onFoot: LocomotionConfig = LocomotionConfig(),
    val mount: MountTuning = MountTuning(),
    val input: InputTuning = InputTuning(),
    val push: PushTuning = PushTuning(),
    val ride: RideTuning = RideTuning(),
    val air: AirTuning = AirTuning(),
    val grind: GrindTuning = GrindTuning(),
    val manual: ManualTuning = ManualTuning(),
    val bail: BailTuning = BailTuning(),
    /** Animation clip per [SkateState] name. */
    val animations: Map<String, String> = mapOf(
        "IDLE" to "idle",
        "PUSH" to "walking",
        "RIDE" to "idle",
        "AIR" to "falling idle",
        "GRIND" to "idle",
        "MANUAL" to "idle",
        "BAIL" to "falling idle",
    ),
)

@Serializable
data class MountTuning(
    /** Max distance in meters between character and board to mount. */
    val range: Float = 1.5f,
    /** Scene object name of the board. */
    val targetName: String = "Skateboard",
    /** Board centre height relative to the character origin (feet) while riding; negative puts it under the feet. */
    val offsetY: Float = -0.05f,
    /** Height of the feet above the ground while riding (board thickness, wheels to deck top). */
    val rideHeight: Float = 0.1f,
    /** Camera framing while walking. */
    val walkCamera: CameraTuning = CameraTuning(4f, 1.4f),
    /** Camera framing while skating (further back, lower). */
    val rideCamera: CameraTuning = CameraTuning(6f, 1.0f),
)

@Serializable
data class CameraTuning(val distance: Float, val height: Float)

@Serializable
data class InputTuning(
    val moveDeadZone: Float = 0.15f,
    /** Stick forward past this value also counts as a push. */
    val pushStickThreshold: Float = 0.6f,
)

@Serializable
data class PushTuning(
    val acceleration: Float = 4f,
    val maxSpeed: Float = 6f,
)

@Serializable
data class RideTuning(
    /** Below this speed (m/s) and without push the skater is idle. */
    val idleSpeed: Float = 0.3f,
    val rollingDrag: Float = 0.6f,
    val brakeDecel: Float = 6f,
    /** Turn rate in rad/s at [slowSpeed] and at [fastSpeed] (linearly interpolated). */
    val turnRateSlow: Float = 3.0f,
    val turnRateFast: Float = 1.2f,
    val slowSpeed: Float = 1f,
    val fastSpeed: Float = 10f,
)

@Serializable
data class AirTuning(
    val ollieImpulse: Float = 300f,
    /** Seconds after leaving the ground before landing can be detected. */
    val minAirTime: Float = 0.15f,
    val coyoteTime: Float = 0.1f,
    val airTurnRate: Float = 1.5f,
    /** Landing heading error in degrees (facing vs travel direction) above which the skater bails. */
    val landingToleranceDeg: Float = 60f,
)

@Serializable
data class GrindTuning(
    val minEntrySpeed: Float = 2f,
    val minHoldSpeed: Float = 1f,
    val maxDurationSec: Float = 4f,
)

@Serializable
data class ManualTuning(
    val minSpeed: Float = 2f,
    /** Gain of the stick correction in degrees/s per unit of stick. */
    val balanceGain: Float = 60f,
    /** Rate at which the balance angle grows away from zero (1/s). */
    val instability: Float = 1.2f,
    val bailAngleDeg: Float = 35f,
)

@Serializable
data class BailTuning(
    val recoverySec: Float = 1.2f,
    val drag: Float = 4f,
)
