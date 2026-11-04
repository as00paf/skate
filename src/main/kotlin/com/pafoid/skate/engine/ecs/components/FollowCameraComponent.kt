package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.gameplay.camera.CameraShake
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f

/**
 * Third-person follow camera (GPL-05), put on a camera object; `FollowCameraSystem` drives it while the scene
 * runs. All settings are plain fields, so they can be tuned in the Properties panel and saved with the scene.
 *
 * - **Follow:** the camera looks at a pivot [pivotHeight] above the object named [targetName], trailing it at
 *   [followSharpness]. When the target is a player whose behaviour gives a camera preset (walking vs riding),
 *   [usePlayerPresets] blends to that preset's distance and height.
 * - **Orbit:** the target's look input (right stick / captured mouse) turns the camera around the pivot, pitch kept
 *   within [minPitch]..[maxPitch] (degrees, + looks down). After [autoAlignDelay] s without look input, while the
 *   target moves, the camera swings back behind it.
 * - **Spring arm:** a sphere of [probeRadius] is swept from the pivot to the camera. The arm shortens at once to stay
 *   in front of whatever it hits (never below [minDistance]), then eases back out at [returnSharpness].
 * - **Shake:** [shake] adds trauma. Impacts on the target at or above [impactShakeImpulse] add [impactShake].
 */
@Serializable
class FollowCameraComponent(
    var targetName: String = "Skater",
    var distance: Float = 4f,
    var pivotHeight: Float = 1.4f,
    var pitch: Float = 12f,
    var minPitch: Float = -20f,
    var maxPitch: Float = 70f,
    var usePlayerPresets: Boolean = true,
    var presetSharpness: Float = 3f,
    var followSharpness: Float = 12f,
    /** Degrees of orbit per unit of look input. */
    var orbitSensitivity: Float = 1f,
    var autoAlign: Boolean = true,
    var autoAlignDelay: Float = 1.5f,
    var autoAlignSharpness: Float = 2.5f,
    /** Target speed (m/s) below which the camera does not swing behind it. */
    var autoAlignMinSpeed: Float = 0.5f,
    var collision: Boolean = true,
    var probeRadius: Float = 0.25f,
    var minDistance: Float = 0.6f,
    var returnSharpness: Float = 4f,
    /** Trauma lost per second. */
    var shakeDecay: Float = 1.2f,
    var shakeMaxOffset: Float = 0.12f,
    var shakeMaxRoll: Float = 2.5f,
    var shakeFrequency: Float = 15f,
    var impactShakeImpulse: Float = 150f,
    var impactShake: Float = 0.5f,
) : Component() {

    /** Runtime state, rebuilt on each Play (not saved, not shown in the inspector). */
    @Transient val state = FollowCameraState()

    /** Adds [amount] (0..1) of shake trauma. */
    fun shake(amount: Float) {
        state.trauma = CameraShake.add(state.trauma, amount)
    }
}

/** Per-play state of a [FollowCameraComponent]; [initialized] false makes the next update snap instead of easing. */
class FollowCameraState {
    var initialized = false
    /** Orbit angles in degrees, camera convention (see [Transform.lookAt]). */
    var yaw = 0f
    var pitch = 0f
    /** Distance and pivot height eased toward the current preset. */
    var distance = 0f
    var pivotHeight = 0f
    val pivot = Vector3f()
    /** Spring-arm length after collision. */
    var armLength = 0f
    var trauma = 0f
    var time = 0f
    /** Seconds since the last look input. */
    var idle = 0f
}
