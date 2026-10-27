package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.gameplay.ground.GroundConfig
import org.joml.Vector2f
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/** Per-frame snapshot a [CharacterBehavior] decides from. Contains no ECS or physics types. */
data class CharacterInput(
    /** Stick/keys, x = right, y = forward, dead zone already applied by the input system. */
    val move: Vector2f = Vector2f(),
    val sprint: Boolean = false,
    val jumpPressed: Boolean = false,
    val jumpHeld: Boolean = false,
    val crouch: Boolean = false,
    val grounded: Boolean = true,
    /** Horizontal speed of the body in m/s. */
    val horizontalSpeed: Float = 0f,
    /** Heading of the horizontal velocity in radians, NaN when (almost) standing still. */
    val velocityYaw: Float = Float.NaN,
    /** Current heading around Y in radians (0 = +Z). */
    val yaw: Float = 0f,
    val cameraForward: Vector3f = Vector3f(0f, 0f, -1f),
    val cameraRight: Vector3f = Vector3f(1f, 0f, 0f),
)

/** What a behavior asks the motion system to apply this frame. */
data class CharacterMotion(
    val state: String,
    /** World horizontal velocity (x/z) to set, or null to leave physics untouched. */
    val horizontalVelocity: Vector3f? = null,
    /** Desired heading in radians; NaN keeps the current one. */
    val targetYaw: Float = Float.NaN,
    /** Smoothing rate in 1/s for reaching [targetYaw]; ignored when [snapYaw]. */
    val yawRate: Float = 0f,
    val snapYaw: Boolean = false,
    val impulse: Vector3f? = null,
    /** Animation clip to play when [state] changed. */
    val animation: String? = null,
)

/** Data-driven character logic. Pure: no window, GL or physics world required. */
interface CharacterBehavior {
    val state: String

    /** Ground probing, slope, step and snapping tuning used by the motion system for this character. */
    val ground: GroundConfig get() = GroundConfig()

    fun update(input: CharacterInput, dt: Float): CharacterMotion
}

/** Registry of behaviors by id so engine components can reference game-specific logic by name. */
object CharacterBehaviors {

    fun interface Factory {
        fun create(configPath: String): CharacterBehavior
    }

    private val logger = Logger.getLogger("CharacterBehaviors")
    private val factories = ConcurrentHashMap<String, Factory>()

    init {
        register(LocomotionBehavior.ID) { path -> LocomotionBehavior(CharacterConfigs.load(path) { LocomotionConfig() }) }
    }

    fun register(id: String, factory: Factory) {
        factories[id] = factory
    }

    fun isRegistered(id: String) = factories.containsKey(id)

    /** Creates the behavior for [id]; falls back to on-foot locomotion when the id is unknown. */
    fun create(id: String, configPath: String): CharacterBehavior {
        val factory = factories[id]
        if (factory == null) {
            logger.warning("Unknown character behavior '$id', falling back to '${LocomotionBehavior.ID}'")
            return factories.getValue(LocomotionBehavior.ID).create(configPath)
        }
        return factory.create(configPath)
    }
}
