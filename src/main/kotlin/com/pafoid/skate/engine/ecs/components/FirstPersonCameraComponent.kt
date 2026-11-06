package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f

/**
 * First-person camera (GPL-22), put on a camera object; `CameraCycleSystem` drives it while the scene runs. The camera
 * sits [eyeHeight] above the object named [targetName], [forwardOffset] ahead of it (clear of the head mesh), looks
 * the way the target faces, tilted down by [pitch] degrees, and eases toward that pose at [followSharpness].
 */
@Serializable
class FirstPersonCameraComponent(
    @GameObjectRef var targetName: String = "Skater",
    var eyeHeight: Float = 1.6f,
    var forwardOffset: Float = 0.25f,
    var pitch: Float = 10f,
    var followSharpness: Float = 25f,
) : Component() {

    /** Runtime state, rebuilt on each Play (not saved, not shown in the inspector). */
    @Transient @kotlin.jvm.Transient val state = FirstPersonCameraState()
}

/** Per-play state of a [FirstPersonCameraComponent]; [initialized] false makes the next update snap. */
class FirstPersonCameraState {
    var initialized = false
    val position = Vector3f()
    /** Camera yaw in degrees ([Transform.lookAt] convention). */
    var yaw = 0f
}
