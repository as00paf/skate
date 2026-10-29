package com.pafoid.skate.engine.gameplay

import org.joml.Vector3f

/** What a behavior asks the motion system to do with its mount (board, vehicle, horse...). */
enum class MountRequest { NONE, MOUNT, DISMOUNT }

/**
 * Implemented by a [CharacterBehavior] that can be mounted on and off something in the scene.
 * The motion system resolves [mountTargetName] to an object, reports its distance through
 * [CharacterInput.mountDistance] and carries out the [MountRequest] the behavior returns.
 */
interface Mountable {
    var mounted: Boolean

    /** Name of the scene object that is mounted. */
    val mountTargetName: String

    /** Offset from the character origin (feet) where the mount is placed when mounting. */
    val mountOffset: Vector3f
}

object MountMath {

    /** True when a mount at [distance] meters can be mounted; NaN (no target in the scene) never can. */
    fun inRange(distance: Float, range: Float): Boolean = !distance.isNaN() && distance <= range

    /** Decides what a toggle press does: dismount when mounted, otherwise mount if in range. */
    fun toggle(mounted: Boolean, pressed: Boolean, distance: Float, range: Float): MountRequest = when {
        !pressed -> MountRequest.NONE
        mounted -> MountRequest.DISMOUNT
        inRange(distance, range) -> MountRequest.MOUNT
        else -> MountRequest.NONE
    }
}
